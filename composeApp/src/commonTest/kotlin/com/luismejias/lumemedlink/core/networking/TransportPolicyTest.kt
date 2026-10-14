package com.luismejias.lumemedlink.core.networking

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class Quiet : NetworkLogSink {
    override fun log(entry: NetworkLogEntry) = Unit
}

private fun build(base: String, transport: TransportPolicy) = lumeHttpClient(
    base,
    MockEngine { respond("") },
    Quiet(),
    securityEvents = NO_SECURITY_EVENTS,
    transport = transport,
)

/** ADR-0062: plain http exists for one place, the local backend from a debug build, and nowhere else. */
class TransportPolicyTest {
    @Test
    fun httpIsRefusedByDefaultEvenTowardTheLocalBackend() {
        assertFailsWith<IllegalArgumentException> { build("http://localhost:3000", TransportPolicy.HttpsOnly) }
    }

    @Test
    fun theLocalPolicyOpensHttpTowardTheThreeLocalNamesOnly() {
        listOf("http://localhost:3000", "http://127.0.0.1:3000", "http://10.0.2.2:3000").forEach {
            build(it, TransportPolicy.LocalCleartext)
        }
        listOf("http://192.168.1.20:3000", "http://api.lume.test", "http://localhost.evil.test").forEach {
            assertFailsWith<IllegalArgumentException>(it) { build(it, TransportPolicy.LocalCleartext) }
        }
    }

    @Test
    fun theDecisionTable() {
        assertTrue(transportAllows(TransportPolicy.HttpsOnly, "https", "api.lume.test"))
        assertFalse(transportAllows(TransportPolicy.HttpsOnly, "http", "localhost"))
        assertTrue(transportAllows(TransportPolicy.LocalCleartext, "http", "10.0.2.2"))
        assertFalse(transportAllows(TransportPolicy.LocalCleartext, "http", "10.0.2.3"))
        assertFalse(transportAllows(TransportPolicy.LocalCleartext, "ftp", "localhost"))
    }
}

/** The local platform's dev token (ADR-0062): asked once, kept in memory, forgotten when refused. */
class DevTokenProviderTest {
    private var fetches = 0
    private var lastBody = ""
    private var answer: Pair<HttpStatusCode, String> = HttpStatusCode.Created to """{"token":"dev-token-1"}"""

    private fun provider(): DevTokenProvider {
        val engine = MockEngine { request ->
            fetches += 1
            lastBody = request.body.toByteArray().decodeToString()
            respond(answer.second, answer.first, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val stack = lumeHttpClient(
            "http://localhost:3000",
            engine,
            Quiet(),
            securityEvents = NO_SECURITY_EVENTS,
            transport = TransportPolicy.LocalCleartext,
        )
        return DevTokenProvider(ContractClient(stack))
    }

    @Test
    fun theTokenIsAskedForOnceAndShared() = runTest {
        val tokens = provider()
        assertEquals("dev-token-1", tokens.token())
        assertEquals("dev-token-1", tokens.token())
        assertEquals(1, fetches)
        assertEquals("""{"userId":"dev-patient","client":"PATIENT"}""", lastBody)
    }

    @Test
    fun aRefusedTokenIsForgottenAndAskedForAgain() = runTest {
        val tokens = provider()
        tokens.token()
        answer = HttpStatusCode.Created to """{"token":"dev-token-2"}"""
        tokens.tokenWasRejected()
        assertEquals("dev-token-2", tokens.token())
        assertEquals(2, fetches)
    }

    @Test
    fun anUnreachablePlatformGivesNoTokenRatherThanAnError() = runTest {
        answer = HttpStatusCode.NotFound to ""
        assertNull(provider().token())
    }
}
