package com.luismejias.lumemedlink.core.session

import com.luismejias.lumemedlink.core.security.SecurityEventEmitter
import com.luismejias.lumemedlink.core.security.SecurityEventKind
import com.luismejias.lumemedlink.core.security.toPlatformKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val WINDOW = 300_000L
private const val T0 = 1_000_000_000_000L

private class LockTestClock(var now: Long = T0) : Clock {
    override fun nowEpochMillis(): Long = now
}

/** Moves with the wall clock so these tests keep describing the ordinary world (ADR-0032). */
private class LockTestElapsedClock(private val wall: LockTestClock) : ElapsedClock {
    override fun elapsedMillis(): Long = wall.now - T0
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

/** The tier-1 store, in memory, with failures a test can switch on. */
private class LedgerStore(private val failOnGet: Boolean = false, private val failOnPut: Boolean = false) :
    SecureStore {
    val entries = mutableMapOf<String, String>()

    override suspend fun put(key: String, value: String) {
        if (failOnPut) error("synthetic store failure")
        entries[key] = value
    }

    override suspend fun get(key: String): String? {
        if (failOnGet) error("synthetic store failure")
        return entries[key]
    }

    override suspend fun remove(key: String) {
        entries.remove(key)
    }

    override suspend fun wipe() {
        entries.clear()
    }
}

private val countKey = SecureStoreKey.FAILED_UNLOCK_ATTEMPTS.storageKey

private fun lockWith(
    gate: UnlockGate,
    clock: LockTestClock = LockTestClock(),
    maxAttempts: Int = 5,
    store: SecureStore = LedgerStore(),
    events: SecurityEventEmitter = SecurityEventEmitter {},
) = SessionLock(
    InactivityLock(WINDOW, clock, LockTestElapsedClock(clock)),
    gate,
    FailedAttemptLedger(store),
    events,
    maxFailedAttempts = maxAttempts,
)

/** A gate whose own material cannot be read — a tampered tier-2 challenge (ADR-0035). */
private class ThrowingUnlockGate : UnlockGate {
    override suspend fun enroll(): Boolean = true

    override suspend fun unlock(): UnlockOutcome = throw SecureStoreUnreadableException()

    override suspend fun clear() = Unit
}

/** Every kind the lock emitted, in order. */
private class RecordedEvents : SecurityEventEmitter {
    val kinds = mutableListOf<SecurityEventKind>()

    override fun emit(kind: SecurityEventKind) {
        kinds += kind
    }
}

/**
 * The lock reports what it decides (task 0018). Until 2026-10-07 nothing emitted `reauthFailure` or
 * `reauthLockout`: the lock produced the result and the shell dropped the reason.
 */
class SessionLockSecurityEventsTest {

    @Test
    fun aRefusedBiometricIsReportedAsAnUnlockFailure() = runTest {
        val events = RecordedEvents()
        val lock = lockWith(ScriptedUnlockGate(UnlockOutcome.Failed), maxAttempts = 3, events = events)

        lock.attemptUnlock()

        assertEquals(listOf(SecurityEventKind.SESSION_UNLOCK_FAILED), events.kinds)
    }

    @Test
    fun theFailureThatExhaustsTheBudgetIsReportedAndSoIsTheLockout() = runTest {
        val events = RecordedEvents()
        val lock = lockWith(ScriptedUnlockGate(UnlockOutcome.Failed), maxAttempts = 1, events = events)

        lock.attemptUnlock()

        assertEquals(
            listOf(SecurityEventKind.SESSION_UNLOCK_FAILED, SecurityEventKind.SESSION_ENDED_UNRECOVERABLE),
            events.kinds,
        )
    }

    @Test
    fun aCancelledPromptAndASuccessReportNothing() = runTest {
        val events = RecordedEvents()
        lockWith(ScriptedUnlockGate(UnlockOutcome.Cancelled), events = events).attemptUnlock()
        lockWith(ScriptedUnlockGate(UnlockOutcome.Unlocked), events = events).attemptUnlock()

        assertTrue(events.kinds.isEmpty(), "cancelling costs nothing, and reports nothing: ${events.kinds}")
    }

    @Test
    fun everyEndedSessionReportsWhatItsReasonMaps() = runTest {
        // Driven by the enum: a reason added without a decision fails to compile in securityEvent()
        // and in the exhaustive `when` below; a reason whose outcome is not emitted fails here.
        SessionEndReason.entries.forEach { reason ->
            val events = RecordedEvents()
            val lock = when (reason) {
                SessionEndReason.TOO_MANY_ATTEMPTS ->
                    lockWith(ScriptedUnlockGate(UnlockOutcome.Failed), maxAttempts = 1, events = events)
                SessionEndReason.ENROLLMENT_CHANGED ->
                    lockWith(ScriptedUnlockGate(UnlockOutcome.Invalidated), events = events)
                SessionEndReason.BIOMETRICS_UNAVAILABLE ->
                    lockWith(ScriptedUnlockGate(UnlockOutcome.Unavailable), events = events)
                SessionEndReason.ATTEMPTS_UNRECORDABLE -> lockWith(
                    ScriptedUnlockGate(UnlockOutcome.Unlocked),
                    store = LedgerStore(failOnGet = true),
                    events = events,
                )
                SessionEndReason.UNLOCK_MATERIAL_UNREADABLE -> lockWith(ThrowingUnlockGate(), events = events)
            }

            assertEquals(LockOutcome.SessionEnded(reason), lock.attemptUnlock(), "scenario for $reason")
            val expected = reason.securityEvent()
            assertEquals(expected, events.kinds.lastOrNull().takeIf { expected != null }, "$reason")
            if (expected == null) {
                assertTrue(
                    SecurityEventKind.SESSION_ENDED_UNRECOVERABLE !in events.kinds,
                    "$reason is the tier working as designed and must not be filed as a lockout",
                )
            }
        }
    }

    @Test
    fun everyKindThePlatformCanHearHasAnEmitterInTheLock() {
        // The three kinds that translate onto the wire are exactly the lock's; if one stops having an
        // emitter (or a fourth kind starts translating) this names it. SECURE_STORE_UNREADABLE is ALSO
        // emitted by the launch probe (app/SessionProbe.kt), which has its own test.
        val emitted = SessionEndReason.entries.mapNotNull { it.securityEvent() }.toSet() +
            SecurityEventKind.SESSION_UNLOCK_FAILED
        val translatable = SecurityEventKind.entries.filter { it.toPlatformKind() != null }.toSet()

        assertEquals(translatable, emitted)
    }
}

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

    // ── ADR-0034: the ceiling lives in the store, so it outlives the process ──────────────────────

    @Test
    fun theCeilingSurvivesTheProcess() = runTest {
        // The defect itself. The count used to live in this object, and this object lives in a
        // composition. Four misses, then the process dies — modelled as a NEW lock over the SAME
        // store, which is everything a restarted app has in common with the one that was killed.
        val store = LedgerStore()
        val beforeTheKill = lockWith(ScriptedUnlockGate(UnlockOutcome.Failed), maxAttempts = 5, store = store)
        repeat(4) { beforeTheKill.attemptUnlock() }

        val afterTheKill = lockWith(ScriptedUnlockGate(UnlockOutcome.Failed), maxAttempts = 5, store = store)

        assertEquals(
            LockOutcome.SessionEnded(SessionEndReason.TOO_MANY_ATTEMPTS),
            afterTheKill.attemptUnlock(),
            "the fifth miss must end the session even when the first four happened in another process",
        )
    }

    @Test
    fun aSpentBudgetEndsTheSessionWithoutAnotherPrompt() = runTest {
        val store = LedgerStore()
        store.put(countKey, "5")
        val gate = ScriptedUnlockGate(UnlockOutcome.Unlocked)

        assertEquals(
            LockOutcome.SessionEnded(SessionEndReason.TOO_MANY_ATTEMPTS),
            lockWith(gate, maxAttempts = 5, store = store).attemptUnlock(),
        )
        assertEquals(0, gate.unlockCalls, "a spent budget must not buy one more try")
    }

    @Test
    fun cancellingWritesNothing() = runTest {
        val store = LedgerStore()
        val lock = lockWith(ScriptedUnlockGate(UnlockOutcome.Cancelled), store = store)

        repeat(3) { lock.attemptUnlock() }

        assertNull(store.entries[countKey], "a dismissal is not an attempt, not even a recorded zero")
    }

    @Test
    fun aCountTheStoreCannotReadEndsTheSessionWithoutPrompting() = runTest {
        val gate = ScriptedUnlockGate(UnlockOutcome.Unlocked)

        val outcome = lockWith(gate, store = LedgerStore(failOnGet = true)).attemptUnlock()

        assertEquals(LockOutcome.SessionEnded(SessionEndReason.ATTEMPTS_UNRECORDABLE), outcome)
        assertEquals(0, gate.unlockCalls, "no prompt is offered by a lock that cannot count it")
    }

    @Test
    fun aMissTheStoreCannotRecordEndsTheSession() = runTest {
        val lock = lockWith(ScriptedUnlockGate(UnlockOutcome.Failed), store = LedgerStore(failOnPut = true))

        assertEquals(
            LockOutcome.SessionEnded(SessionEndReason.ATTEMPTS_UNRECORDABLE),
            lock.attemptUnlock(),
            "a miss that cannot be counted would be a free one",
        )
    }

    @Test
    fun aCorruptCountIsNotAFreshBudget() = runTest {
        val store = LedgerStore()
        store.put(countKey, "not-a-number")

        assertEquals(
            LockOutcome.SessionEnded(SessionEndReason.ATTEMPTS_UNRECORDABLE),
            lockWith(ScriptedUnlockGate(UnlockOutcome.Unlocked), store = store).attemptUnlock(),
        )
    }

    @Test
    fun aSuccessfulUnlockForgetsThePersistedMisses() = runTest {
        val store = LedgerStore()
        val gate = ScriptedUnlockGate(
            mutableListOf(UnlockOutcome.Failed, UnlockOutcome.Failed, UnlockOutcome.Unlocked),
        )
        val lock = lockWith(gate, store = store)

        repeat(3) { lock.attemptUnlock() }

        assertNull(store.entries[countKey])
    }

    @Test
    fun sessionEndedForgetsThePersistedMisses() = runTest {
        val store = LedgerStore()
        val lock = lockWith(ScriptedUnlockGate(UnlockOutcome.Failed), store = store)
        lock.attemptUnlock()

        lock.sessionEnded()

        assertNull(store.entries[countKey], "the next session starts with the whole budget")
    }

    @Test
    fun aGateThatCannotEvenAskEndsTheSessionInsteadOfCrashing() = runTest {
        // ADR-0035: the Android tier-2 challenge now THROWS when tampered. Before 2026-10-07 any
        // exception from the gate escaped attemptUnlock() into the shell's coroutine.
        val throwing = object : UnlockGate {
            override suspend fun enroll(): Boolean = true
            override suspend fun unlock(): UnlockOutcome = throw SecureStoreUnreadableException()
            override suspend fun clear() = Unit
        }

        assertEquals(
            LockOutcome.SessionEnded(SessionEndReason.UNLOCK_MATERIAL_UNREADABLE),
            lockWith(throwing).attemptUnlock(),
        )
    }
}
