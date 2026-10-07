package com.luismejias.lumemedlink.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luismejias.lumemedlink.core.logging.LogDetail
import com.luismejias.lumemedlink.core.logging.LogEvent
import com.luismejias.lumemedlink.core.logging.LumeLogSink
import com.luismejias.lumemedlink.core.security.SecurityEventKind
import com.luismejias.lumemedlink.core.security.SecurityEventReporter
import com.luismejias.lumemedlink.core.session.InstallSentinel
import com.luismejias.lumemedlink.core.session.KeystoreSecureStore
import com.luismejias.lumemedlink.core.session.RefreshClient
import com.luismejias.lumemedlink.core.session.SecureStoreKey
import com.luismejias.lumemedlink.core.session.SecureStoreUnreadableException
import com.luismejias.lumemedlink.core.session.SessionManager
import com.luismejias.lumemedlink.core.session.SessionTokens
import com.luismejias.lumemedlink.core.session.TokenStore
import com.luismejias.lumemedlink.core.session.tamperWithStoredEntry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * A tampered tier-1 entry is a SECURITY EVENT, not a first launch (task `0003`, ADR-0035).
 *
 * Until 2026-10-07 a blob whose GCM tag did not close read as `null` — "never written" — so the app
 * showed a sign-in screen and nothing else knew. The three things the task asks, on the real store:
 * the value does not come back, nothing reaches the UI as a crash, and SECURE_STORE_UNREADABLE is
 * produced.
 */
@RunWith(AndroidJUnit4::class)
class UnreadableStoreOnDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = KeystoreSecureStore(context)

    @AfterTest
    fun cleanUp() = runBlocking { store.wipe() }

    @Test
    fun aTamperedEntryIsReportedNotForgotten() = runBlocking {
        store.wipe()
        val manager = SessionManager(TokenStore(store), NeverRefreshes)
        manager.establish(SessionTokens("acc-synthetic", "ref-synthetic", Long.MAX_VALUE))
        tamperWithStoredEntry(context, SecureStoreKey.SESSION_TOKENS)

        assertFailsWith<SecureStoreUnreadableException> { store.get(SecureStoreKey.SESSION_TOKENS.storageKey) }

        val sink = RecordingSink()
        val reporter = RecordingReporter()
        val freshManager = SessionManager(TokenStore(store), NeverRefreshes)
        val hasSession = probeSession(freshManager, Established, store, sink, reporter)

        assertFalse(hasSession, "a tampered store opens no session")
        assertEquals(listOf(SecurityEventKind.SECURE_STORE_UNREADABLE), reporter.kinds, "the signal §8.16 exists for")
        assertEquals(listOf(LogEvent.SECURE_STORE_UNREADABLE), sink.events)
    }

    private object NeverRefreshes : RefreshClient {
        override suspend fun refresh(refreshToken: String): SessionTokens? = null
    }

    private object Established : InstallSentinel {
        override suspend fun hasRunBefore(): Boolean = true
        override suspend fun markHasRun() = Unit
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
}
