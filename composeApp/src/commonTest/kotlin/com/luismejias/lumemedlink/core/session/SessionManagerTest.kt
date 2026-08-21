package com.luismejias.lumemedlink.core.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// All data below is synthetic (§9).

private const val NOW = 1_000_000_000_000L

private class FakeSecureStore : SecureStore {
    val entries = mutableMapOf<String, String>()

    override suspend fun put(key: String, value: String) {
        entries[key] = value
    }

    override suspend fun get(key: String): String? = entries[key]

    override suspend fun remove(key: String) {
        entries.remove(key)
    }

    override suspend fun wipe() {
        entries.clear()
    }
}

private class FakeRefreshClient(private val result: () -> SessionTokens?) : RefreshClient {
    var calls = 0
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun refresh(refreshToken: String): SessionTokens? {
        calls += 1
        gate?.await()
        return result()
    }
}

private class FixedClock(var now: Long = NOW) : Clock {
    override fun nowEpochMillis(): Long = now
}

private fun tokens(access: String = "access-1", refresh: String = "refresh-1", expiresAt: Long = NOW + 900_000) =
    SessionTokens(access, refresh, expiresAt)

private fun freshPair() = tokens(access = "access-2", refresh = "refresh-2", expiresAt = NOW + 1_800_000)

class SessionManagerTest {

    @Test
    fun tokenIsNullWithoutASession() = runTest {
        val manager = SessionManager(TokenStore(FakeSecureStore()), FakeRefreshClient { null }, FixedClock())

        assertNull(manager.token())
        assertFalse(manager.hasSession())
    }

    @Test
    fun validAccessTokenIsServedWithoutRefreshing() = runTest {
        val refreshClient = FakeRefreshClient { freshPair() }
        val manager = SessionManager(TokenStore(FakeSecureStore()), refreshClient, FixedClock())
        manager.establish(tokens())

        assertEquals("access-1", manager.token())
        assertEquals(0, refreshClient.calls)
    }

    @Test
    fun expiredAccessTokenTriggersOneRefresh() = runTest {
        val clock = FixedClock()
        val refreshClient = FakeRefreshClient { freshPair() }
        val manager = SessionManager(TokenStore(FakeSecureStore()), refreshClient, clock)
        manager.establish(tokens(expiresAt = NOW - 1))

        assertEquals("access-2", manager.token())
        assertEquals(1, refreshClient.calls)
    }

    @Test
    fun expiryHonorsTheSkewMargin() = runTest {
        val clock = FixedClock()
        val refreshClient = FakeRefreshClient { freshPair() }
        val manager = SessionManager(TokenStore(FakeSecureStore()), refreshClient, clock)
        // Expires 10s from now — inside the 30s skew window, so it must already count as expired.
        manager.establish(tokens(expiresAt = NOW + 10_000))

        assertEquals("access-2", manager.token())
        assertEquals(1, refreshClient.calls)
    }

    @Test
    fun concurrentCallersShareOneRefresh() = runTest {
        val refreshClient = FakeRefreshClient { freshPair() }
        refreshClient.gate = CompletableDeferred()
        val manager = SessionManager(TokenStore(FakeSecureStore()), refreshClient, FixedClock())
        manager.establish(tokens(expiresAt = NOW - 1))

        val callers = (1..5).map { async { manager.token() } }
        yield()
        refreshClient.gate?.complete(Unit)
        val results = callers.awaitAll()

        assertEquals(List(5) { "access-2" }, results)
        assertEquals(1, refreshClient.calls, "single-flight: N concurrent callers, ONE refresh")
    }

    @Test
    fun rejectionForcesARefreshEvenWithAValidAccessToken() = runTest {
        val refreshClient = FakeRefreshClient { freshPair() }
        val manager = SessionManager(TokenStore(FakeSecureStore()), refreshClient, FixedClock())
        manager.establish(tokens())

        manager.tokenWasRejected()

        assertEquals("access-2", manager.token())
        assertEquals(1, refreshClient.calls)
        // The flag is consumed: the next call serves the fresh token without another refresh.
        assertEquals("access-2", manager.token())
        assertEquals(1, refreshClient.calls)
    }

    @Test
    fun rejectedRefreshKillsTheSession() = runTest {
        val store = FakeSecureStore()
        val manager = SessionManager(TokenStore(store), FakeRefreshClient { null }, FixedClock())
        manager.establish(tokens(expiresAt = NOW - 1))

        assertNull(manager.token())
        assertFalse(manager.hasSession())
        assertTrue(store.entries.isEmpty(), "a definitively rejected refresh wipes the persisted pair")
    }

    @Test
    fun logoutIsAWipeContract() = runTest {
        val store = FakeSecureStore()
        val manager = SessionManager(TokenStore(store), FakeRefreshClient { freshPair() }, FixedClock())
        manager.establish(tokens())

        manager.logout()

        assertNull(manager.token(), "memory half")
        assertTrue(store.entries.isEmpty(), "disk half")
        assertFalse(manager.hasSession())
    }

    @Test
    fun sessionSurvivesAProcessRestartViaTheStore() = runTest {
        val store = FakeSecureStore()
        SessionManager(TokenStore(store), FakeRefreshClient { freshPair() }, FixedClock())
            .establish(tokens())

        // A new manager over the same store: the persisted pair loads lazily.
        val rebooted = SessionManager(TokenStore(store), FakeRefreshClient { freshPair() }, FixedClock())
        assertTrue(rebooted.hasSession())
        assertEquals("access-1", rebooted.token())
    }

    @Test
    fun transportFailureLeavesTheSessionUntouched() = runTest {
        val store = FakeSecureStore()
        val throwingClient = object : RefreshClient {
            var calls = 0

            override suspend fun refresh(refreshToken: String): SessionTokens? {
                calls += 1
                error("synthetic transport failure")
            }
        }
        val manager = SessionManager(TokenStore(store), throwingClient, FixedClock())
        manager.establish(tokens(expiresAt = NOW - 1))

        var thrown = false
        val job = launch {
            try {
                manager.token()
            } catch (_: IllegalStateException) {
                thrown = true
            }
        }
        job.join()

        assertTrue(thrown)
        assertTrue(manager.hasSession(), "not refreshed, state unchanged — retry on the next call")
        assertEquals(1, throwingClient.calls)
    }
}
