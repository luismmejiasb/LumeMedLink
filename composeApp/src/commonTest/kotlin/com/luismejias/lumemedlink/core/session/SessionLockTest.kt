package com.luismejias.lumemedlink.core.session

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val WINDOW = 300_000L
private const val T0 = 1_000_000_000_000L

private class LockTestClock(var now: Long = T0) : Clock {
    override fun nowEpochMillis(): Long = now
}

private class ScriptedUnlockGate(private var outcomes: MutableList<UnlockOutcome>) : UnlockGate {
    var enrolled = false
    var cleared = false
    var unlockCalls = 0

    constructor(vararg outcomes: UnlockOutcome) : this(outcomes.toMutableList())

    override suspend fun enroll(): Boolean {
        enrolled = true
        return true
    }

    override suspend fun unlock(): UnlockOutcome {
        unlockCalls += 1
        return if (outcomes.size > 1) outcomes.removeAt(0) else outcomes.first()
    }

    override suspend fun clear() {
        cleared = true
    }
}

private fun lockWith(gate: UnlockGate, clock: LockTestClock = LockTestClock(), maxAttempts: Int = 5) =
    SessionLock(InactivityLock(WINDOW, clock), gate, maxAttempts)

class SessionLockTest {

    @Test
    fun bornLocked() {
        assertTrue(lockWith(ScriptedUnlockGate(UnlockOutcome.Unlocked)).isLocked())
    }

    @Test
    fun successfulUnlockOpensTheWindow() = runTest {
        val lock = lockWith(ScriptedUnlockGate(UnlockOutcome.Unlocked))

        assertEquals(LockOutcome.Unlocked, lock.attemptUnlock())
        assertFalse(lock.isLocked())
    }

    @Test
    fun cancellingCostsNothing() = runTest {
        val gate = ScriptedUnlockGate(UnlockOutcome.Cancelled)
        val lock = lockWith(gate)

        repeat(20) { lock.attemptUnlock() }

        // The whole point of ADR-0020's mirror: putting the phone down is not a wrong finger, so
        // twenty dismissals must not consume a single attempt nor end the session.
        assertEquals(LockOutcome.StillLocked(remainingAttempts = null), lock.attemptUnlock())
        assertTrue(lock.isLocked())
    }

    @Test
    fun failedAttemptsCountDownAndReportRemaining() = runTest {
        val lock = lockWith(ScriptedUnlockGate(UnlockOutcome.Failed), maxAttempts = 3)

        assertEquals(LockOutcome.StillLocked(remainingAttempts = 2), lock.attemptUnlock())
        assertEquals(LockOutcome.StillLocked(remainingAttempts = 1), lock.attemptUnlock())
    }

    @Test
    fun exhaustingAttemptsEndsTheSession() = runTest {
        val lock = lockWith(ScriptedUnlockGate(UnlockOutcome.Failed), maxAttempts = 3)

        lock.attemptUnlock()
        lock.attemptUnlock()

        assertEquals(
            LockOutcome.SessionEnded(SessionEndReason.TOO_MANY_ATTEMPTS),
            lock.attemptUnlock(),
        )
        assertTrue(lock.isLocked(), "an ended session is still locked, never quietly open")
    }

    @Test
    fun cancellationsDoNotConsumeAttemptsBetweenFailures() = runTest {
        val gate = ScriptedUnlockGate(
            mutableListOf(
                UnlockOutcome.Failed,
                UnlockOutcome.Cancelled,
                UnlockOutcome.Cancelled,
                UnlockOutcome.Failed,
                UnlockOutcome.Failed,
            ),
        )
        val lock = lockWith(gate, maxAttempts = 3)

        assertEquals(LockOutcome.StillLocked(remainingAttempts = 2), lock.attemptUnlock())
        assertEquals(LockOutcome.StillLocked(remainingAttempts = null), lock.attemptUnlock())
        assertEquals(LockOutcome.StillLocked(remainingAttempts = null), lock.attemptUnlock())
        assertEquals(LockOutcome.StillLocked(remainingAttempts = 1), lock.attemptUnlock())
        assertEquals(
            LockOutcome.SessionEnded(SessionEndReason.TOO_MANY_ATTEMPTS),
            lock.attemptUnlock(),
        )
    }

    @Test
    fun successResetsTheAttemptCounter() = runTest {
        val gate = ScriptedUnlockGate(
            mutableListOf(
                UnlockOutcome.Failed,
                UnlockOutcome.Failed,
                UnlockOutcome.Unlocked,
                UnlockOutcome.Failed,
            ),
        )
        val lock = lockWith(gate, maxAttempts = 3)

        lock.attemptUnlock()
        lock.attemptUnlock()
        assertEquals(LockOutcome.Unlocked, lock.attemptUnlock())
        // Back to a full budget: the two earlier misses must not haunt the next lock cycle.
        assertEquals(LockOutcome.StillLocked(remainingAttempts = 2), lock.attemptUnlock())
    }

    @Test
    fun changedEnrollmentEndsTheSessionImmediately() = runTest {
        val lock = lockWith(ScriptedUnlockGate(UnlockOutcome.Invalidated))

        assertEquals(
            LockOutcome.SessionEnded(SessionEndReason.ENROLLMENT_CHANGED),
            lock.attemptUnlock(),
            "a newly enrolled face must never inherit the previous session (ADR-0005)",
        )
    }

    @Test
    fun unavailableBiometricsEndTheSessionRatherThanOpen() = runTest {
        val lock = lockWith(ScriptedUnlockGate(UnlockOutcome.Unavailable))

        assertEquals(
            LockOutcome.SessionEnded(SessionEndReason.BIOMETRICS_UNAVAILABLE),
            lock.attemptUnlock(),
            "fail closed: no way to re-authenticate means the session cannot stay alive",
        )
    }

    @Test
    fun activityCannotUnlockAnExpiredWindow() = runTest {
        val clock = LockTestClock()
        val lock = lockWith(ScriptedUnlockGate(UnlockOutcome.Unlocked), clock)
        lock.attemptUnlock()

        clock.now = T0 + WINDOW + 1
        lock.recordActivity()

        assertTrue(lock.isLocked(), "touching the screen is not re-authentication")
    }

    @Test
    fun activitySlidesTheWindowWhileUnlocked() = runTest {
        val clock = LockTestClock()
        val lock = lockWith(ScriptedUnlockGate(UnlockOutcome.Unlocked), clock)
        lock.attemptUnlock()

        clock.now = T0 + WINDOW - 1
        lock.recordActivity()
        clock.now = T0 + WINDOW + 1

        assertFalse(lock.isLocked())
    }

    @Test
    fun sessionEndedReturnsToBornLocked() = runTest {
        val clock = LockTestClock()
        val lock = lockWith(ScriptedUnlockGate(UnlockOutcome.Unlocked), clock)
        lock.attemptUnlock()

        lock.sessionEnded()

        assertTrue(lock.isLocked())
    }

    @Test
    fun sessionEstablishedClearsPriorFailures() = runTest {
        val gate = ScriptedUnlockGate(
            mutableListOf(UnlockOutcome.Failed, UnlockOutcome.Failed, UnlockOutcome.Failed),
        )
        val lock = lockWith(gate, maxAttempts = 3)
        lock.attemptUnlock()
        lock.attemptUnlock()

        lock.sessionEstablished()

        assertFalse(lock.isLocked())
        assertEquals(LockOutcome.StillLocked(remainingAttempts = 2), lock.attemptUnlock())
    }
}
