package com.luismejias.lumemedlink.core.session

import com.luismejias.lumemedlink.core.security.SecurityEventEmitter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val ENTRY_NOW = 1_000_000L

/** The tier-1 store, in memory, with each operation able to fail. */
private class EntryTestStore(private val failOnWipe: Boolean = false, private val failOnPut: Boolean = false) :
    SecureStore {
    val entries = mutableMapOf<String, String>()
    val calls = mutableListOf<String>()

    override suspend fun put(key: String, value: String) {
        calls += "put:$key"
        if (failOnPut) error("synthetic store failure")
        entries[key] = value
    }

    override suspend fun get(key: String): String? = entries[key]

    override suspend fun remove(key: String) {
        entries.remove(key)
    }

    override suspend fun wipe() {
        calls += "wipe"
        if (failOnWipe) error("synthetic store failure")
        entries.clear()
    }
}

private class EntryTestGate(
    private val canEnroll: Boolean = true,
    private val failOnClear: Boolean = false,
    private val log: MutableList<String>,
) : UnlockGate {
    var enrolled = false

    override suspend fun enroll(): Boolean {
        log += "enroll"
        enrolled = canEnroll
        return canEnroll
    }

    override suspend fun unlock(): UnlockOutcome = UnlockOutcome.Unlocked

    override suspend fun clear() {
        log += "clear"
        if (failOnClear) error("synthetic gate failure")
        enrolled = false
    }
}

private class EntryTestClock : Clock {
    override fun nowEpochMillis(): Long = ENTRY_NOW
}

private class EntryTestElapsed : ElapsedClock {
    override fun elapsedMillis(): Long = 0L
}

private class RejectingRefresh : RefreshClient {
    override suspend fun refresh(refreshToken: String): SessionTokens? = null
}

private fun entryTokens(expiresAt: Long = ENTRY_NOW + 900_000) =
    SessionTokens("acc-synthetic", "ref-synthetic", expiresAt)

/**
 * A session begins on an empty slate, with its tier-2 material, or not at all (ADR-0037; tasks 0005
 * and 0020). The two defects this pins: `enroll()` had no caller, so the first lock would have ended
 * every session; and `establish()` wrote over whatever the previous account left in the store.
 */
class SessionEntryTest {

    private class Fixture(
        store: EntryTestStore = EntryTestStore(),
        canEnroll: Boolean = true,
        failOnClear: Boolean = false,
    ) {
        val store = store
        val gate = EntryTestGate(canEnroll, failOnClear, store.calls)
        val manager = SessionManager(TokenStore(store), RejectingRefresh(), EntryTestClock())
        val lock = SessionLock(
            InactivityLock(300_000L, EntryTestClock(), EntryTestElapsed()),
            gate,
            FailedAttemptLedger(store),
            SecurityEventEmitter {},
        )

        suspend fun enter() = establishSession(entryTokens(), manager, store, gate, lock)
    }

    @Test
    fun aSessionBeginsWithItsTier2MaterialAndAnOpenWindow() = runTest {
        val f = Fixture()

        assertEquals(SessionEntryOutcome.Established, f.enter())

        assertTrue(f.gate.enrolled, "the tier-2 key was never made: the first lock would end the session")
        assertTrue(f.manager.hasSession())
        assertFalse(f.lock.isLocked(), "a fresh login is a real authentication: the window opens")
    }

    @Test
    fun whatTheLastSessionLeftIsErasedBeforeAnythingIsWritten() = runTest {
        val f = Fixture()
        // What a dead session leaves: a rejected refresh erases only the token entry (ADR-0036), so the
        // rest stays under the same fixed names the next account will use.
        f.store.entries["a_future_cache_v1"] = "synthetic-account-A"
        f.store.entries[SecureStoreKey.FAILED_UNLOCK_ATTEMPTS.storageKey] = "4"

        f.enter()

        assertNull(f.store.entries["a_future_cache_v1"], "account B began on account A's remains")
        assertEquals(
            listOf("wipe", "clear", "enroll"),
            f.store.calls.take(3),
            "the slate is cleared, then the old tier-2 key, then the new one made — before any write",
        )
        assertNull(f.store.entries[SecureStoreKey.FAILED_UNLOCK_ATTEMPTS.storageKey])
    }

    @Test
    fun noBiometricsMeansNoSessionAndNothingWritten() = runTest {
        val f = Fixture(canEnroll = false)

        assertEquals(SessionEntryOutcome.Refused(SessionEntryRefusal.TIER2_UNAVAILABLE), f.enter())

        assertFalse(f.manager.hasSession())
        assertNull(f.store.entries[SecureStoreKey.SESSION_TOKENS.storageKey], "tokens written for a refused session")
        assertTrue(f.lock.isLocked())
    }

    @Test
    fun aSlateThatCannotBeClearedRefusesTheSession() = runTest {
        listOf(Fixture(EntryTestStore(failOnWipe = true)), Fixture(failOnClear = true)).forEach { f ->
            assertEquals(SessionEntryOutcome.Refused(SessionEntryRefusal.SLATE_NOT_CLEARED), f.enter())
            assertFalse(f.gate.enrolled)
            assertNull(f.store.entries[SecureStoreKey.SESSION_TOKENS.storageKey])
        }
    }

    @Test
    fun tokensThatCannotBeSavedLeaveNoHalfSessionBehind() = runTest {
        val f = Fixture(EntryTestStore(failOnPut = true))

        assertEquals(SessionEntryOutcome.Refused(SessionEntryRefusal.SESSION_NOT_SAVED), f.enter())

        assertFalse(f.gate.enrolled, "the tier-2 key of a refused session survived")
        assertFalse(f.manager.hasSession())
        assertTrue(f.lock.isLocked())
    }

    @Test
    fun aRejectedRefreshTellsTheShellTheSessionEnded() = runTest {
        val f = Fixture()
        f.enter()
        assertFalse(f.manager.sessionEnded.value)

        f.manager.tokenWasRejected()
        assertNull(f.manager.token(), "the refresh client rejects, so there is no token")

        assertTrue(f.manager.sessionEnded.value, "the shell was not told: it would keep showing the session")

        // And a new session clears the signal.
        f.enter()
        assertFalse(f.manager.sessionEnded.value)
    }
}
