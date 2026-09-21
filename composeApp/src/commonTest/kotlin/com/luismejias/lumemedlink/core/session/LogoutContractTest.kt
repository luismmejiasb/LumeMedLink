package com.luismejias.lumemedlink.core.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A store that can be told to fail, or to stall inside the wipe until a test lets it through. */
private class LogoutTestStore(
    private val failOnWipe: Boolean = false,
    private val pauseInWipe: CompletableDeferred<Unit>? = null,
) : SecureStore {
    val entries = mutableMapOf<String, String>()
    var wipeCalls = 0

    override suspend fun put(key: String, value: String) {
        entries[key] = value
    }

    override suspend fun get(key: String): String? = entries[key]

    override suspend fun remove(key: String) {
        entries.remove(key)
    }

    override suspend fun wipe() {
        wipeCalls += 1
        pauseInWipe?.await()
        if (failOnWipe) error("synthetic store failure")
        entries.clear()
    }
}

private class LogoutTestGate(private val failOnClear: Boolean = false) : UnlockGate {
    var cleared = false

    override suspend fun enroll(): Boolean = true

    override suspend fun unlock(): UnlockOutcome = UnlockOutcome.Unlocked

    override suspend fun clear() {
        if (failOnClear) error("synthetic gate failure")
        cleared = true
    }
}

private class LogoutTestClock(var now: Long = 1_000L) : Clock {
    override fun nowEpochMillis(): Long = now
}

private class LogoutTestRefreshClient : RefreshClient {
    override suspend fun refresh(refreshToken: String): SessionTokens? = null
}

/**
 * The logout contract of ADR-0014, pinned at the level where it was actually broken.
 *
 * The contract had a device test and a common test and was still wrong, which is why this file
 * exists and what it is shaped against: the device test asserted on `wipe()` — a method no
 * production path called — and the one that took the real path asserted neither the files nor the
 * key, though its name promised both. Both were green for three weeks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogoutContractTest {

    private fun fixture(
        store: SecureStore,
        gate: UnlockGate = LogoutTestGate(),
    ): Triple<SessionManager, UnlockGate, SessionLock> {
        val manager = SessionManager(TokenStore(store), LogoutTestRefreshClient(), LogoutTestClock())
        val lock = SessionLock(InactivityLock(windowMillis = 300_000L, clock = LogoutTestClock()), gate)
        return Triple(manager, gate, lock)
    }

    @Test
    fun theWholeNamespaceGoesNotOnlyTheTokenEntry() = runTest {
        // THE REGRESSION THIS FILE EXISTS FOR. The old logout called remove(SESSION_TOKENS), so the
        // tier-2 challenge — and on Android the tier-1 key behind every entry — survived a logout
        // that three ADRs described as erasing them.
        val store = LogoutTestStore()
        SecureStoreKey.entries.forEach { store.put(it.storageKey, "synthetic-${it.name}") }
        assertEquals(SecureStoreKey.entries.size, store.entries.size, "precondition: every key written")
        val (manager, gate, lock) = fixture(store)
        manager.establish(SessionTokens("acc-synthetic", "ref-synthetic", Long.MAX_VALUE))

        val outcome = performLogout(manager, store, gate, lock)

        assertTrue(outcome.complete, "failed steps: ${outcome.failed}")
        SecureStoreKey.entries.forEach {
            assertNull(store.get(it.storageKey), "${it.name} survived the logout")
        }
        assertEquals(1, store.wipeCalls, "logout must erase the namespace, not one entry")
        assertNull(manager.token(), "memory half of the contract")
        assertFalse(manager.hasSession())
        assertTrue((gate as LogoutTestGate).cleared, "tier-2 material survived")
        assertTrue(lock.isLocked(), "the next session must start born-locked")
    }

    @Test
    fun aFailingStepDoesNotSkipTheOnesAfterIt() = runTest {
        // The old sequence was four inline calls in the shell: the first throw skipped the rest,
        // so a store that failed left the tier-2 key and the open lock window alive while the UI
        // had already returned to Login.
        val store = LogoutTestStore(failOnWipe = true)
        val (manager, gate, lock) = fixture(store)
        manager.establish(SessionTokens("acc-synthetic", "ref-synthetic", Long.MAX_VALUE))

        val outcome = performLogout(manager, store, gate, lock)

        assertEquals(setOf(LogoutStep.SECRETS), outcome.failed)
        assertFalse(outcome.complete)
        assertTrue((gate as LogoutTestGate).cleared, "the step AFTER the failure did not run")
        assertTrue(lock.isLocked(), "the lock step after the failure did not run")
    }

    @Test
    fun everyFailingStepIsNamed() = runTest {
        val store = LogoutTestStore(failOnWipe = true)
        val (manager, gate, lock) = fixture(store, LogoutTestGate(failOnClear = true))

        val outcome = performLogout(manager, store, gate, lock)

        assertEquals(setOf(LogoutStep.SECRETS, LogoutStep.TIER2_MATERIAL), outcome.failed)
    }

    @Test
    fun aCancelledCallerStillGetsTheWholeErase() = runTest {
        // Remove NonCancellable from performLogout and this test fails: the await below throws on
        // cancellation, the wipe never finishes, and the two steps after it never run. That is the
        // shape the shell had — endSession() ran in rememberCoroutineScope(), which dies with the
        // composition, so backgrounding the app mid-logout truncated the erase exactly when the
        // phone is most likely to be leaving its owner's hands.
        val release = CompletableDeferred<Unit>()
        val store = LogoutTestStore(pauseInWipe = release)
        SecureStoreKey.entries.forEach { store.put(it.storageKey, "synthetic-${it.name}") }
        val (manager, gate, lock) = fixture(store)
        manager.establish(SessionTokens("acc-synthetic", "ref-synthetic", Long.MAX_VALUE))

        val caller = launch { performLogout(manager, store, gate, lock) }
        runCurrent()
        assertEquals(1, store.wipeCalls, "precondition: the erase is in flight and suspended")

        caller.cancel()
        release.complete(Unit)
        runCurrent()

        assertTrue(store.entries.isEmpty(), "the erase was truncated by the caller's cancellation")
        assertTrue((gate as LogoutTestGate).cleared, "tier-2 material survived a cancelled logout")
        assertTrue(lock.isLocked(), "the lock step was skipped by a cancelled logout")
    }
}
