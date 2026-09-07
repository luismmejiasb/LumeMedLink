package com.luismejias.lumemedlink.app

import com.luismejias.lumemedlink.core.logging.LogDetail
import com.luismejias.lumemedlink.core.logging.LogEvent
import com.luismejias.lumemedlink.core.logging.LumeLogSink
import com.luismejias.lumemedlink.core.security.SecurityEventKind
import com.luismejias.lumemedlink.core.security.SecurityEventReporter
import com.luismejias.lumemedlink.core.session.InstallSentinel
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
// Synthetic, and shaped like a real stored pair on purpose: the previous installation left this
// behind in the Keychain, and the probe must never hand it to the app (§9, ADR-0028).
private const val INHERITED_TOKENS =
    """{"accessToken":"synthetic-a","refreshToken":"synthetic-r","expiresAtEpochMillis":9999999999999}"""

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

    /**
     * A container that has run before — the ORDINARY launch. Every test except the fresh-install one
     * needs it: without it the probe purges first and never reaches the behaviour under test, so
     * each of those tests would pass for the wrong reason (F7, ADR-0028).
     */
    private object EstablishedContainer : InstallSentinel {
        override suspend fun hasRunBefore(): Boolean = true

        override suspend fun markHasRun() = Unit
    }

    private object FreshContainer : InstallSentinel {
        var marked = false

        override suspend fun hasRunBefore(): Boolean = marked

        override suspend fun markHasRun() {
            marked = true
        }
    }

    private fun managerOver(store: SecureStore) = SessionManager(TokenStore(store), NeverRefreshes)

    @Test
    fun anUnreadableStoreResolvesToNoSession() = runTest {
        val sink = RecordingSink()
        val reporter = RecordingReporter()

        val store = ThrowingStore(IllegalStateException("boom"))
        val result = probeSession(managerOver(store), EstablishedContainer, store, sink, reporter)

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
            val store = ThrowingStore(CancellationException("cancelled"))
            probeSession(managerOver(store), EstablishedContainer, store, sink, reporter)
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

        val result = probeSession(managerOver(EmptyStore), EstablishedContainer, EmptyStore, sink, reporter)

        assertFalse(result, "No tokens means no session.")
        assertTrue(
            sink.events.isEmpty() && reporter.kinds.isEmpty(),
            "CONTROL: the ordinary first launch must NOT report a security event. Without this " +
                "assertion the test above would still pass if the probe reported unconditionally.",
        )
    }

    @Test
    fun aFreshContainerNeverReportsAnInheritedSession() = runTest {
        val sink = RecordingSink()
        val reporter = RecordingReporter()
        FreshContainer.marked = false
        val store = object : SecureStore {
            var wiped = false
            override suspend fun put(key: String, value: String) = Unit

            // A session IS sitting there — on iOS that is the previous install's, surviving in the
            // Keychain after the app was deleted.
            override suspend fun get(key: String): String? = if (wiped) null else INHERITED_TOKENS
            override suspend fun remove(key: String) = Unit
            override suspend fun wipe() {
                wiped = true
            }
        }

        val result = probeSession(managerOver(store), FreshContainer, store, sink, reporter)

        assertFalse(
            result,
            "A reinstall must never resume the previous installation's session. Answering `true` " +
                "here is the app greeting the last person who owned the phone (§8.13, §8.17).",
        )
        assertTrue(store.wiped, "and the inherited secrets must actually be gone, not merely ignored")
        assertTrue(FreshContainer.marked, "and the container must be marked so the next launch is ordinary")
    }
}
