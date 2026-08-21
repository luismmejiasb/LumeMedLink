package com.luismejias.lumemedlink.core.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlin.random.Random

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
 * Hardening, in order: https-only fails CLOSED at construction (an `http://` base throws here,
 * not at call time) and again per-request for absolute URLs; redirects are never followed;
 * timeouts are explicit; retries are bounded and GET-only; every non-2xx maps to the [AppError]
 * taxonomy via [AppErrorException] — RFC 9457 `detail` never crosses out of this layer.
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

        install(lumeStackGuard(logSink, tokenProvider))

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
private fun lumeStackGuard(logSink: NetworkLogSink, tokenProvider: TokenProvider?) =
    createClientPlugin("LumeStackGuard") {
        onRequest { request, _ ->
            check(request.url.protocol == URLProtocol.HTTPS) {
                "LumeHttpStack is https-only and fails closed: refused ${request.url.protocol.name}://"
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
                NetworkLogEntry(
                    method = request.method.value,
                    // encodedPath only: the query never reaches a log line (§8.1).
                    path = request.url.encodedPath,
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
