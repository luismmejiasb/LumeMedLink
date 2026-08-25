package com.luismejias.lumemedlink.core.security

import com.luismejias.lumemedlink.core.networking.NetworkLogEntry
import com.luismejias.lumemedlink.core.networking.NetworkLogSink
import com.luismejias.lumemedlink.core.networking.lumeHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// All data below is synthetic (§9).

private const val BASE_URL = "https://api.test.lume"

private class SilentLogSink : NetworkLogSink {
    val entries = mutableListOf<NetworkLogEntry>()
    override fun log(entry: NetworkLogEntry) {
        entries += entry
    }
}

/**
 * The contract double: a scripted stand-in for the platform, driven through the REAL hardened
 * stack rather than around it.
 *
 * This is the shape the rest of the client half will be built against while the backend builds
 * its side. It is not a mock of the reporter — the reporter under test is the production one, and
 * every request it makes travels through `lumeHttpClient`, so origin pinning, the redacted log,
 * the timeouts and the no-retry-on-POST rule are all in play. What is faked is the far end, which
 * is the only part this repo does not own.
 */
private class ContractDouble(private val reply: (HttpRequestData) -> Pair<HttpStatusCode, String>) {
    val received = mutableListOf<HttpRequestData>()
    val bodies = mutableListOf<String>()
    val logSink = SilentLogSink()

    fun client(): HttpClient {
        val engine = MockEngine { request ->
            received += request
            bodies += request.body.toByteArrayText()
            val (status, body) = reply(request)
            respond(content = body, status = status)
        }
        return lumeHttpClient(baseUrl = BASE_URL, engine = engine, logSink = logSink)
    }
}

private fun io.ktor.http.content.OutgoingContent.toByteArrayText(): String = when (this) {
    is io.ktor.http.content.TextContent -> text
    is io.ktor.http.content.ByteArrayContent -> bytes().decodeToString()
    else -> ""
}

class HttpSecurityEventReporterTest {

    // ── The doctrine: it never throws ───────────────────────────────────────────────────────────

    @Test
    fun aServerErrorIsNotTheCallersProblem() = runTest {
        val double = ContractDouble { HttpStatusCode.InternalServerError to "" }

        HttpSecurityEventReporter(double.client()).report(SecurityEventKind.SESSION_UNLOCK_FAILED)

        assertEquals(
            1,
            double.received.size,
            "CONTROL: the request must actually have been attempted. Without this, a reporter " +
                "that quietly did nothing at all would pass every assertion below.",
        )
    }

    @Test
    fun aDeadNetworkIsNotTheCallersProblem() = runTest {
        val engine = MockEngine { throw IOException("no route to host") }
        val client = lumeHttpClient(BASE_URL, engine, SilentLogSink())

        // No assertion needed beyond "this line returns". Every caller of this channel is in the
        // middle of applying a protection (locking a session, refusing an origin, wiping a store);
        // an exception here lands in code written assuming the FIRST failure was the emergency.
        HttpSecurityEventReporter(client).report(SecurityEventKind.NETWORK_ORIGIN_REFUSED)
    }

    @Test
    fun everyKindSurvivesAFailingServer() = runTest {
        val double = ContractDouble { HttpStatusCode.BadRequest to "" }
        val reporter = HttpSecurityEventReporter(double.client())

        // Exhaustive by construction: a kind added later is covered the day it is added, which is
        // the same trick SecureStoreKey uses to keep the wipe test honest.
        SecurityEventKind.entries.forEach { reporter.report(it) }

        assertEquals(SecurityEventKind.entries.size, double.received.size)
    }

    // ── But a cancellation is NOT swallowed ─────────────────────────────────────────────────────

    /**
     * The first version of this test made the ENGINE throw a CancellationException and asserted it
     * came back out. It failed — and the failure was the test's, not the code's. An engine does not
     * cancel; a *caller's scope* does, and by then Ktor has already wrapped whatever the engine
     * threw. Asserting on the wrapped shape would have pinned a Ktor internal instead of the
     * property that matters.
     *
     * The property that matters is this: when the caller's scope is cancelled while `report` is
     * suspended, the caller does not continue as if nothing happened. `reachedTheNextLine` is the
     * discriminator — a swallowed cancellation lets the assignment run, a re-thrown one does not.
     */
    @Test
    fun aCancelledCallerDoesNotCarryOn() = runTest {
        val engine = MockEngine { awaitCancellation() }
        val client = lumeHttpClient(BASE_URL, engine, SilentLogSink())
        var reachedTheNextLine = false

        val job = launch {
            HttpSecurityEventReporter(client).report(SecurityEventKind.SESSION_ENDED_UNRECOVERABLE)
            reachedTheNextLine = true
        }
        yield()
        job.cancelAndJoin()

        assertFalse(
            reachedTheNextLine,
            "The catch-all must not eat the cancellation of the scope it is running in (§6). If " +
                "it does, work continues inside a scope that is being torn down.",
        )
    }

    @Test
    fun anUncancelledCallerDoesCarryOn() = runTest {
        val double = ContractDouble { HttpStatusCode.Accepted to "" }
        var reachedTheNextLine = false

        val job = launch {
            HttpSecurityEventReporter(double.client()).report(SecurityEventKind.SESSION_UNLOCK_FAILED)
            reachedTheNextLine = true
        }
        job.join()

        assertTrue(
            reachedTheNextLine,
            "CONTROL: without this, a `report` that always threw would pass the test above.",
        )
    }

    // ── What crosses the wire ───────────────────────────────────────────────────────────────────

    @Test
    fun onlyTheOpaqueKindCrosses() = runTest {
        val double = ContractDouble { HttpStatusCode.Accepted to "" }

        HttpSecurityEventReporter(double.client()).report(SecurityEventKind.SESSION_UNLOCK_INVALIDATED)

        val body = double.bodies.single()
        assertTrue(
            body.contains("SESSION_UNLOCK_INVALIDATED"),
            "The kind is the message. Body was: $body",
        )
        // The body is one field. This asserts the *shape*, not the field name — the name is the
        // platform's and is still unknown (backend-requests/0004). A body with two fields means
        // somebody added one, and that is exactly the change that must not pass unnoticed.
        assertEquals(
            1,
            body.count { it == ':' },
            "A security-event body with more than one field is how free text gets in (ADR-0023). " +
                "Body was: $body",
        )
    }

    @Test
    fun itIsAPostAndItDoesNotRetry() = runTest {
        val double = ContractDouble { HttpStatusCode.ServiceUnavailable to "" }

        HttpSecurityEventReporter(double.client()).report(SecurityEventKind.NETWORK_INTERSTITIAL_DETECTED)

        assertEquals(HttpMethod.Post, double.received.single().method)
        assertEquals(
            1,
            double.received.size,
            "A 503 must NOT be retried on this route. The backend has no idempotency key here " +
                "(trap T13), so a retry is how one event becomes three.",
        )
    }

    // ── It travels through the hardened stack, not around it ────────────────────────────────────

    @Test
    fun theRequestIsLoggedWithoutABody() = runTest {
        val double = ContractDouble { HttpStatusCode.Accepted to "" }

        HttpSecurityEventReporter(double.client()).report(SecurityEventKind.SECURE_STORE_UNREADABLE)

        val logged = double.logSink.entries.single().toString()
        assertFalse(
            logged.contains("SECURE_STORE_UNREADABLE"),
            "Going through the stack means the redacted log applies. If the kind appears in the " +
                "log line, this reporter is writing its own telemetry path. Line was: $logged",
        )
    }
}
