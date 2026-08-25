package com.luismejias.lumemedlink.app

import com.luismejias.lumemedlink.core.logging.LogDetail
import com.luismejias.lumemedlink.core.logging.LogEvent
import com.luismejias.lumemedlink.core.logging.LumeLogSink
import com.luismejias.lumemedlink.core.security.SecurityEventKind
import com.luismejias.lumemedlink.core.security.SecurityEventReporter
import com.luismejias.lumemedlink.core.session.RefreshClient
import com.luismejias.lumemedlink.core.session.SecureStore
import com.luismejias.lumemedlink.core.session.SessionManager
import com.luismejias.lumemedlink.core.session.SessionTokens
import com.luismejias.lumemedlink.core.session.TokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The launch probe's three branches (ADR-0025).
 *
 * This test exists because the property it guards was a bare expression inside a `LaunchedEffect`
 * until the iOS host killed the process on it. Being un-assertable was not incidental to that
 * failure — nothing could have caught it before a device did.
 */
class SessionProbeTest {

    private class ThrowingStore(private val failure: Throwable) : SecureStore {
        override suspend fun put(key: String, value: String) = throw failure
        override suspend fun get(key: String): String? = throw failure
        override suspend fun remove(key: String) = throw failure
        override suspend fun wipe() = throw failure
    }

    private object EmptyStore : SecureStore {
        override suspend fun put(key: String, value: String) = Unit
        override suspend fun get(key: String): String? = null
        override suspend fun remove(key: String) = Unit
        override suspend fun wipe() = Unit
    }

    private object NeverRefreshes : RefreshClient {
        override suspend fun refresh(refreshToken: String): SessionTokens? = null
    }

    private class RecordingSink : LumeLogSink {
        val events = mutableListOf<LogEvent>()
        override fun log(event: LogEvent, detail: LogDetail?) {
            events += event
        }
    }

    private class RecordingReporter : SecurityEventReporter {
        val kinds = mutableListOf<SecurityEventKind>()
        override suspend fun report(kind: SecurityEventKind) {
            kinds += kind
        }
    }

    private fun managerOver(store: SecureStore) = SessionManager(TokenStore(store), NeverRefreshes)

    @Test
    fun anUnreadableStoreResolvesToNoSession() = runTest {
        val sink = RecordingSink()
        val reporter = RecordingReporter()

        val result = probeSession(managerOver(ThrowingStore(IllegalStateException("boom"))), sink, reporter)

        assertFalse(
            result,
            "A store this app cannot read must cost a sign-in, never grant one. `false` is the " +
                "Login destination; anything else would be failing open on a broken store.",
        )
        assertEquals(
            listOf(LogEvent.SECURE_STORE_UNREADABLE),
            sink.events,
            "Degrading silently is how a permanently broken keychain stays invisible.",
        )
        assertEquals(
            listOf(SecurityEventKind.SECURE_STORE_UNREADABLE),
            reporter.kinds,
            "The event channel exists for exactly this (§8.16).",
        )
    }

    @Test
    fun cancellationIsRethrownNotSwallowed() = runTest {
        val sink = RecordingSink()
        val reporter = RecordingReporter()

        assertFailsWith<CancellationException> {
            probeSession(managerOver(ThrowingStore(CancellationException("cancelled"))), sink, reporter)
        }

        assertTrue(
            sink.events.isEmpty() && reporter.kinds.isEmpty(),
            "A cancellation is not a security event, and reporting it would be noise on every " +
                "screen the doctor leaves early. Swallowing it would break structured " +
                "concurrency (§6) — the trap F12 was caught by.",
        )
    }

    @Test
    fun aHealthyEmptyStoreIsStillNoSession() = runTest {
        val sink = RecordingSink()
        val reporter = RecordingReporter()

        val result = probeSession(managerOver(EmptyStore), sink, reporter)

        assertFalse(result, "No tokens means no session.")
        assertTrue(
            sink.events.isEmpty() && reporter.kinds.isEmpty(),
            "CONTROL: the ordinary first launch must NOT report a security event. Without this " +
                "assertion the test above would still pass if the probe reported unconditionally.",
        )
    }
}
