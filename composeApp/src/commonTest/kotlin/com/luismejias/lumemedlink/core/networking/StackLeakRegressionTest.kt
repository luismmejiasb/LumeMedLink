package com.luismejias.lumemedlink.core.networking

import com.luismejias.lumemedlink.shared.AppError
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.fail

// Regressions found by an adversarial review of the F12 fix itself. Every one of these passed
// review and shipped; each test below is written to FAIL against the shipped code (§9, synthetic).

private const val LEAKY_URL =
    "https://api.test.lume/v1/orgs/clinica-alemana/patients/11111111-1/appointments?rut=11111111-1"
private const val BASE = "https://api.test.lume"

private class Sink : NetworkLogSink {
    val entries = mutableListOf<NetworkLogEntry>()

    override fun log(entry: NetworkLogEntry) {
        entries += entry
    }
}

class StackLeakRegressionTest {

    @Test
    fun aTimeoutDoesNotDeliverTheUrlToTheCaller() = runTest {
        // THE HEADLINE CASE OF ADR-0016, and the fix did not cover it: Ktor's
        // HttpRequestTimeoutException extends CancellationException, and the guard rethrows
        // cancellation untouched. The earlier test used a throwing engine (IOException), which the
        // guard DOES catch — so it passed while this stayed open.
        // FAITHFUL to how a timeout actually happens: the engine stalls and HttpTimeout fires,
        // rather than the engine throwing the exception itself. The first version of this test did
        // the latter, and passed — a test passing for the wrong reason, which is the failure mode
        // this repo keeps meeting.
        val engine = MockEngine {
            delay(60_000)
            respond("never")
        }
        val client = lumeHttpClient(BASE, engine, Sink())

        val thrown = runCatching {
            client.get("/v1/orgs/clinica-alemana/patients/11111111-1/appointments?rut=11111111-1") {
                timeout { requestTimeoutMillis = 50 }
            }
        }.exceptionOrNull() ?: fail("expected a failure")

        assertFalse(thrown.message.orEmpty().contains("11111111"), "identifier reached the caller")
        assertFalse(thrown.message.orEmpty().contains("api.test.lume"), "host reached the caller")
        assertIs<AppErrorException>(thrown, "a timeout must arrive as the taxonomy, not as Ktor's type")
    }

    @Test
    fun boundedRetryStillWorksForTransportFailures() = runTest {
        // The F12 fix mapped IOException to AppErrorException inside the retry loop, so
        // `retryOnExceptionIf { cause is IOException }` stopped matching and bounded retry on
        // transport failure went dead — a feature broken while closing a leak.
        // Counting handler invocations, not requestHistory: MockEngine does not record a request
        // whose handler threw, so the earlier assertion read 0 and measured nothing.
        var attempts = 0
        val engine = MockEngine {
            attempts += 1
            throw IOException("connect failed")
        }
        val client = lumeHttpClient(BASE, engine, Sink())

        runCatching { client.get("/v1/agenda") }

        assertEquals(3, attempts, "1 call + 2 bounded retries on transport failure")
    }

    @Test
    fun anOrgSlugIsNotARouteWord() = runTest {
        // redactPath's allowlist was written to describe route words, and a tenant slug like
        // `clinica-alemana` matches it exactly — so an org identifier landed in every log line.
        val engine = MockEngine { respond("ok") }
        val sink = Sink()
        val client = lumeHttpClient(BASE, engine, sink)

        client.get("/v1/orgs/clinica-alemana/appointments")

        assertFalse(
            sink.entries.single().path.contains("clinica-alemana"),
            "the tenant slug identifies the clinic and must be redacted like any other id",
        )
    }

    @Test
    fun anOriginRefusalArrivesAsTheTaxonomyAndIsLogged() = runTest {
        // A refusal was invisible: nothing logged, no taxonomy, a bare IllegalStateException that
        // a caller cannot distinguish from a programming error.
        val engine = MockEngine { respond("ok") }
        val sink = Sink()
        val client = lumeHttpClient(BASE, engine, sink)

        val thrown = runCatching { client.get("//evil.test/steal") }.exceptionOrNull() ?: fail("expected refusal")

        val mapped = assertIs<AppErrorException>(thrown, "refusals belong in the AppError taxonomy")
        assertIs<AppError.Blocked>(mapped.error)
        assertEquals(1, sink.entries.size, "a blocked request must leave a trace")
        assertFalse(sink.entries.single().toString().contains("evil.test"), "but never the host")
    }

    @Test
    fun aNonJsonBodyDoesNotDeliverTheUrlToTheCaller() = runTest {
        // A captive portal or a proxy error page returns 200 with text/html. Ktor throws
        // NoTransformationFoundException from the RECEIVE stage — outside the Send hook the F12 fix
        // installed — and its message carries the full URL.
        val engine = MockEngine {
            respond(
                content = "<html>hotel wifi</html>",
                status = HttpStatusCode.OK,
                headers = io.ktor.http.headersOf(io.ktor.http.HttpHeaders.ContentType, "text/html"),
            )
        }
        val client = lumeHttpClient(BASE, engine, Sink())

        val thrown = runCatching {
            client.get("/v1/orgs/clinica-alemana/patients/11111111-1/appointments?rut=11111111-1")
                .body<Map<String, String>>()
        }.exceptionOrNull() ?: fail("expected a failure")

        assertFalse(thrown.message.orEmpty().contains("11111111"), "identifier reached the caller")
        assertFalse(thrown.message.orEmpty().contains("api.test.lume"), "host reached the caller")
    }
}
