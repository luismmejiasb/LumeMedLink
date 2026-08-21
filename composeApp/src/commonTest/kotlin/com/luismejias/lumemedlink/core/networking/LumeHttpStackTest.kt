package com.luismejias.lumemedlink.core.networking

import com.luismejias.lumemedlink.shared.AppError
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// All data below is synthetic (§9: a real person's datum in a test is an incident).

private const val BASE_URL = "https://api.test.lume"

private class RecordingLogSink : NetworkLogSink {
    val entries = mutableListOf<NetworkLogEntry>()

    override fun log(entry: NetworkLogEntry) {
        entries += entry
    }
}

private class FakeTokenProvider(private val fixedToken: String?) : TokenProvider {
    var rejectedCount = 0

    override suspend fun token(): String? = fixedToken

    override fun tokenWasRejected() {
        rejectedCount += 1
    }
}

private fun MockRequestHandleScope.respondProblemJson(status: HttpStatusCode, body: String): HttpResponseData = respond(
    content = body,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, "application/problem+json"),
)

private suspend fun stackError(block: suspend () -> Unit): AppError =
    assertFailsWith<AppErrorException> { block() }.error

class LumeHttpStackTest {

    // ── https-only, fail closed ─────────────────────────────────────────────────────────────────

    @Test
    fun httpBaseUrlRefusesToBuild() {
        val engine = MockEngine { respond("never reached") }
        assertFailsWith<IllegalArgumentException> {
            lumeHttpClient("http://api.test.lume", engine, RecordingLogSink())
        }
    }

    // ── redirects are never followed ────────────────────────────────────────────────────────────

    @Test
    fun redirectIsNotFollowed() = runTest {
        val engine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.Found,
                headers = headersOf(HttpHeaders.Location, "https://evil.test/elsewhere"),
            )
        }
        val client = lumeHttpClient(BASE_URL, engine, RecordingLogSink())

        val error = stackError { client.get("/v1/agenda") }

        assertIs<AppError.Unexpected>(error)
        assertEquals(302, error.status)
        assertEquals(1, engine.requestHistory.size, "the 302 must end the call, not start another")
    }

    // ── status mapping (RFC 9457 → AppError) ────────────────────────────────────────────────────

    @Test
    fun status401MapsToAuthExpiredAndSignalsTheProvider() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.Unauthorized) }
        val provider = FakeTokenProvider("tok-101")
        val client = lumeHttpClient(BASE_URL, engine, RecordingLogSink(), provider)

        val error = stackError { client.get("/v1/me") }

        assertIs<AppError.AuthExpired>(error)
        assertEquals(1, provider.rejectedCount)
    }

    @Test
    fun status422ProblemJsonMapsToValidationAndDetailNeverLeaks() = runTest {
        val secretDetail = "validation failed for field rut: synthetic value 22222222-2 rejected"
        val engine = MockEngine {
            respondProblemJson(
                status = HttpStatusCode.UnprocessableEntity,
                body = """
                    {
                      "type": "https://problems.test.lume/validation",
                      "title": "Validation failed",
                      "status": 422,
                      "detail": "$secretDetail"
                    }
                """.trimIndent(),
            )
        }
        val client = lumeHttpClient(BASE_URL, engine, RecordingLogSink())

        val exception = assertFailsWith<AppErrorException> { client.post("/v1/profile") }

        val error = assertIs<AppError.Validation>(exception.error)
        assertEquals("https://problems.test.lume/validation", error.problemType)
        // The server's prose stops at the networking layer: not in the error, not in the message.
        assertFalse(exception.message.contains(secretDetail))
        assertFalse(error.toString().contains(secretDetail))
    }

    @Test
    fun status404MapsToNotFound() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.NotFound) }
        val client = lumeHttpClient(BASE_URL, engine, RecordingLogSink())

        assertIs<AppError.NotFound>(stackError { client.get("/v1/patients/absent") })
    }

    @Test
    fun status500MapsToRetryable() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.InternalServerError) }
        val client = lumeHttpClient(BASE_URL, engine, RecordingLogSink())

        val error = stackError { client.get("/v1/agenda") }

        assertIs<AppError.Retryable>(error)
        assertEquals(500, error.status)
    }

    @Test
    fun malformedProblemJsonFallsBackToStatusMapping() = runTest {
        val engine = MockEngine {
            respondProblemJson(HttpStatusCode.UnprocessableEntity, body = "{not json at all")
        }
        val client = lumeHttpClient(BASE_URL, engine, RecordingLogSink())

        val error = assertIs<AppError.Validation>(stackError { client.get("/v1/profile") })
        assertNull(error.problemType)
    }

    // ── bounded retry, GET only ─────────────────────────────────────────────────────────────────

    @Test
    fun getRetriesAreBounded() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.InternalServerError) }
        val client = lumeHttpClient(BASE_URL, engine, RecordingLogSink())

        assertIs<AppError.Retryable>(stackError { client.get("/v1/agenda") })
        assertEquals(3, engine.requestHistory.size, "1 call + 2 bounded retries, never more")
    }

    @Test
    fun getRetryStopsAtFirstSuccess() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls += 1
            if (calls < 3) respond("", HttpStatusCode.InternalServerError) else respond("ok")
        }
        val client = lumeHttpClient(BASE_URL, engine, RecordingLogSink())

        val body = client.get("/v1/agenda").bodyAsText()

        assertEquals("ok", body)
        assertEquals(3, engine.requestHistory.size)
    }

    @Test
    fun postNeverRetries() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.InternalServerError) }
        val client = lumeHttpClient(BASE_URL, engine, RecordingLogSink())

        assertIs<AppError.Retryable>(stackError { client.post("/v1/profile") })
        assertEquals(1, engine.requestHistory.size, "a non-idempotent call must never be retried")
    }

    // ── bearer seam + redacted log ──────────────────────────────────────────────────────────────

    @Test
    fun bearerIsAttachedAndLogIsRedacted() = runTest {
        val secretToken = "tok-secret-abc123"
        val engine = MockEngine { respond("ok") }
        val sink = RecordingLogSink()
        val client = lumeHttpClient(BASE_URL, engine, sink, FakeTokenProvider(secretToken))

        client.get("/v1/agenda?rut=11111111-1&from=2026-01-01")

        val sent: HttpRequestData = engine.requestHistory.single()
        assertEquals("Bearer $secretToken", sent.headers[HttpHeaders.Authorization])

        val entry = sink.entries.single()
        assertEquals("GET", entry.method)
        assertEquals("/v1/agenda", entry.path, "the query string must never reach a log line")
        assertEquals(200, entry.status)
        assertEquals(sent.headers[RID_HEADER], entry.rid, "the log line correlates by the sent rid")
        val line = entry.toString()
        assertFalse(line.contains(secretToken))
        assertFalse(line.contains("rut"))
        assertFalse(line.contains("11111111"))
    }

    @Test
    fun anonymousClientSendsNoAuthorizationHeader() = runTest {
        val engine = MockEngine { respond("ok") }
        val client = lumeHttpClient(BASE_URL, engine, RecordingLogSink(), tokenProvider = null)

        client.get("/v1/app-availability")

        assertNull(engine.requestHistory.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun everyRequestCarriesAFreshRid() = runTest {
        val engine = MockEngine { respond("ok") }
        val client = lumeHttpClient(BASE_URL, engine, RecordingLogSink())

        client.get("/v1/a")
        client.get("/v1/b")

        val rids = engine.requestHistory.map { it.headers[RID_HEADER] }
        assertEquals(2, rids.size)
        assertTrue(rids.none { it.isNullOrBlank() })
        assertTrue(rids[0] != rids[1], "rids are per logical request")
    }
}
