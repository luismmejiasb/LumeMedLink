package com.luismejias.lumemedlink.core.networking

import com.luismejias.lumemedlink.shared.AppError
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * RFC 9457 body, decoded tolerantly (an unknown field costs nothing). `detail` and `title` are
 * decoded because they are part of the media type, and they STOP here: [toAppError] never copies
 * them out — the taxonomy carries structure, not server prose.
 */
@Serializable
internal data class ProblemJson(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
)

/** Thrown by the stack for any non-2xx response, already mapped to the taxonomy. */
internal class AppErrorException(val error: AppError) : Exception() {
    // CORRECTED 2026-08-21 (F20). This comment used to say the taxonomy "carries no server prose",
    // which was true of `detail` and `title` and FALSE of `type`: `problemType` copies a
    // server-chosen string into the error, and interpolating the whole data class put it in the
    // message — i.e. in any crash report and any generic catch. A backend that ever templates
    // `/problems/validation/rut/11111111-1` would have put a RUT there, and no gate here would
    // have seen it.
    //
    // Now the message names only the CASE, never its data, so the property holds by construction
    // instead of by trusting the shape of someone else's URIs. `problemType` still lives on the
    // error for a caller that wants to branch on it — that is a deliberate, narrow trust.
    override val message: String = "HTTP call failed: ${error::class.simpleName}"
}

private val problemJsonFormat = Json { ignoreUnknownKeys = true }

/**
 * Maps a non-2xx response to the taxonomy. Reads the body ONLY when the server declared
 * `application/problem+json` (RFC 9457); anything else maps by status alone — the stack does not
 * guess at bodies it was not promised.
 */
internal suspend fun HttpResponse.toAppError(): AppError {
    val problem = problemOrNull()
    return when (status.value) {
        401 -> AppError.AuthExpired
        400, 422 -> AppError.Validation(problemType = problem?.type)
        404 -> AppError.NotFound
        408, 425, 429, in 500..599 -> AppError.Retryable(status = status.value)
        // A 3xx lands here too: redirects are never followed (ADR-0004), so one arriving is an
        // anomaly to surface, not to chase.
        else -> AppError.Unexpected(status = status.value, problemType = problem?.type)
    }
}

private suspend fun HttpResponse.problemOrNull(): ProblemJson? {
    val declared = contentType() ?: return null
    val isProblemJson = declared.match(ContentType("application", "problem+json"))
    if (!isProblemJson) return null
    // A malformed problem body must not turn a mapped error into a crash: fall back to
    // status-only mapping.
    return try {
        problemJsonFormat.decodeFromString<ProblemJson>(bodyAsText())
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
