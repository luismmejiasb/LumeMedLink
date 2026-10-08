package com.luismejias.lumemedlink.core.session

import com.luismejias.lumemedlink.core.security.SecurityEventEmitter
import com.luismejias.lumemedlink.core.security.SecurityEventKind
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException

/** Default ceiling on wrong-biometric attempts before the session is ended (F4). */
private const val DEFAULT_MAX_FAILED_ATTEMPTS = 5

/** Why a lock attempt ended the session. Carried so the UI can say the true reason. */
internal enum class SessionEndReason {
    /** Too many wrong biometric attempts. */
    TOO_MANY_ATTEMPTS,

    /** The enrolled biometrics changed: the tier-2 material is gone by design (ADR-0005). */
    ENROLLMENT_CHANGED,

    /** No usable biometrics on this device — re-entry is impossible, so the session cannot stay. */
    BIOMETRICS_UNAVAILABLE,

    /**
     * The failed-attempt count could not be read or written, so the ceiling cannot be enforced — and a
     * ceiling that cannot be enforced is not offered (ADR-0034).
     */
    ATTEMPTS_UNRECORDABLE,

    /**
     * The gate could not even ask: its own material is unreadable — a tampered tier-2 challenge
     * (ADR-0035) — or it failed in a way nobody classified. Until 2026-10-07 that exception escaped
     * the lock into the shell's coroutine.
     */
    UNLOCK_MATERIAL_UNREADABLE,
}

/**
 * What the platform hears when a lock attempt ends the session, by reason (task 0018). `null` is a
 * decision, not a gap — each one says why:
 *
 * - [SessionEndReason.ENROLLMENT_CHANGED] is the tier working AS DESIGNED, and the platform has no name
 *   for it (`SESSION_UNLOCK_INVALIDATED` translates to nothing; `backend-requests/0006` asks for one).
 *   Filing it as a lockout would raise an alarm over a healthy device.
 *
 * Exhaustive with no `else`: a new reason stops compiling until somebody decides what it reports.
 */
internal fun SessionEndReason.securityEvent(): SecurityEventKind? = when (this) {
    SessionEndReason.TOO_MANY_ATTEMPTS -> SecurityEventKind.SESSION_ENDED_UNRECOVERABLE
    SessionEndReason.BIOMETRICS_UNAVAILABLE -> SecurityEventKind.SESSION_ENDED_UNRECOVERABLE
    // The store could not keep the count, or the gate's own material could not be read: both are a
    // stored secret that could not be read back or written, which is what the platform's
    // `securityStorageFailure` names.
    SessionEndReason.ATTEMPTS_UNRECORDABLE -> SecurityEventKind.SECURE_STORE_UNREADABLE
    SessionEndReason.UNLOCK_MATERIAL_UNREADABLE -> SecurityEventKind.SECURE_STORE_UNREADABLE
    SessionEndReason.ENROLLMENT_CHANGED -> null
}

/** The result of asking the lock to let the user back in. */
internal sealed interface LockOutcome {
    data object Unlocked : LockOutcome

    /** Still locked; the user may try again. [remainingAttempts] is null when nothing was spent. */
    data class StillLocked(val remainingAttempts: Int?) : LockOutcome

    /** The session is over; the shell must return to Login and the caller must wipe. */
    data class SessionEnded(val reason: SessionEndReason) : LockOutcome
}

/**
 * The policy layer of F4: it owns WHEN the app is locked ([InactivityLock]) and WHAT an unlock
 * attempt means ([UnlockGate]). Pure coordination, no platform types — so every branch of the
 * policy is pinned by a test on both targets, which is the point: the security decisions live
 * here, in code a test can reach, instead of inside a platform callback nobody can run in CI.
 *
 * The fail directions follow the family table (never re-derived by intuition):
 * - Born locked, and it stays locked unless real key material comes back.
 * - **Cancelling costs nothing.** Dismissing the prompt is not a failed attempt (ADR-0020 mirror).
 * - Wrong biometrics count, and enough of them end the session rather than allow infinite tries. The
 *   count lives in the tier-1 store, not in this object, so killing the process does not reset it
 *   (ADR-0034) — and a count the store cannot keep ends the session instead of being forgotten.
 * - Enrollment changed or biometrics unavailable end the session immediately: in both cases
 *   re-entry is impossible or would be inherited by a new identity, and a lock that cannot tell
 *   whether it should open, stays closed.
 *
 * And it REPORTS what it decided, here where the fact is born (task 0018): every refused biometric,
 * and every ended session by [SessionEndReason.securityEvent]. Until 2026-10-07 the lock produced the
 * result and the shell discarded the reason, so `reauthFailure` and `reauthLockout` had no emitter.
 */
internal class SessionLock(
    private val inactivityLock: InactivityLock,
    private val unlockGate: UnlockGate,
    private val attempts: FailedAttemptLedger,
    private val events: SecurityEventEmitter,
    private val maxFailedAttempts: Int = DEFAULT_MAX_FAILED_ATTEMPTS,
) {
    fun isLocked(): Boolean = inactivityLock.isLocked()

    /**
     * How long until the window closes on its own. The shell sleeps on this instead of only
     * re-reading the lock when a finger touches the screen (ADR-0032).
     */
    fun millisUntilLock(): Long = inactivityLock.millisUntilLock()

    /** Slides the inactivity window. Cannot unlock — only a real re-auth can (see [InactivityLock]). */
    fun recordActivity() {
        inactivityLock.recordActivity()
    }

    /**
     * Opens the window after a successful login, and clears any attempt history. Throws when the
     * store cannot clear the count: the caller is the login flow, and a login that leaves a spent
     * budget behind should know it.
     */
    suspend fun sessionEstablished() {
        attempts.clear()
        inactivityLock.unlock()
    }

    /**
     * Returns to the born-locked state, and clears the attempt count. Part of logout. The window is
     * closed FIRST, so a store that throws on the count can leave a spent budget behind but never an
     * open window; the logout contract reports the step as failed either way.
     */
    suspend fun sessionEnded() {
        inactivityLock.reset()
        attempts.clear()
    }

    suspend fun attemptUnlock(): LockOutcome = decideUnlock().also { outcome ->
        if (outcome is LockOutcome.SessionEnded) outcome.reason.securityEvent()?.let(events::emit)
    }

    private suspend fun decideUnlock(): LockOutcome {
        // Read BEFORE prompting: a budget spent in an earlier process ends the session here, without
        // offering one more try. A count the store cannot read ends it too — fail closed.
        val spent = spentAttempts() ?: return LockOutcome.SessionEnded(SessionEndReason.ATTEMPTS_UNRECORDABLE)
        if (spent >= maxFailedAttempts) return LockOutcome.SessionEnded(SessionEndReason.TOO_MANY_ATTEMPTS)

        return when (askTheGate() ?: return LockOutcome.SessionEnded(SessionEndReason.UNLOCK_MATERIAL_UNREADABLE)) {
            UnlockOutcome.Unlocked -> {
                // Best effort: a count that survives a success costs the NEXT lock some attempts, which
                // is the safe direction, so a store hiccup here must not keep the doctor out.
                forgetAttempts()
                inactivityLock.unlock()
                LockOutcome.Unlocked
            }

            UnlockOutcome.Cancelled -> LockOutcome.StillLocked(remainingAttempts = null)

            // Free, like a dismissal, and for the same reason: nobody failed (ADR-0040).
            UnlockOutcome.NotNow -> LockOutcome.StillLocked(remainingAttempts = null)

            UnlockOutcome.Failed -> {
                // Reported whatever comes next: a refused biometric is the fact, and a lockout it may
                // cause is a second fact, reported on its own.
                events.emit(SecurityEventKind.SESSION_UNLOCK_FAILED)
                val nowSpent = spent + 1
                // Written BEFORE the verdict is returned, so the only window in which a killed process
                // loses this attempt is the one between the OS's answer and this line.
                when {
                    !recordAttempts(nowSpent) -> LockOutcome.SessionEnded(SessionEndReason.ATTEMPTS_UNRECORDABLE)
                    nowSpent >= maxFailedAttempts -> LockOutcome.SessionEnded(SessionEndReason.TOO_MANY_ATTEMPTS)
                    else -> LockOutcome.StillLocked(remainingAttempts = maxFailedAttempts - nowSpent)
                }
            }

            UnlockOutcome.Invalidated -> LockOutcome.SessionEnded(SessionEndReason.ENROLLMENT_CHANGED)

            UnlockOutcome.Unavailable -> LockOutcome.SessionEnded(SessionEndReason.BIOMETRICS_UNAVAILABLE)
        }
    }

    // The three below turn a store that throws into the answer the policy needs. The exception itself
    // is dropped on purpose: its message is a platform's free text, and §8.1 allows one logging path
    // with a closed vocabulary. Cancellation is re-thrown first, by type and by asking the job, because
    // a store that wraps a platform call can convert it on the way out (ADR-0026).

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun askTheGate(): UnlockOutcome? = try {
        unlockGate.unlock()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (unreadable: Throwable) {
        currentCoroutineContext().ensureActive()
        null
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun spentAttempts(): Int? = try {
        attempts.spent()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (unreadable: Throwable) {
        currentCoroutineContext().ensureActive()
        null
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun recordAttempts(spent: Int): Boolean = try {
        attempts.record(spent)
        true
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (unwritable: Throwable) {
        currentCoroutineContext().ensureActive()
        false
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun forgetAttempts() {
        try {
            attempts.clear()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (unwritable: Throwable) {
            currentCoroutineContext().ensureActive()
        }
    }
}
