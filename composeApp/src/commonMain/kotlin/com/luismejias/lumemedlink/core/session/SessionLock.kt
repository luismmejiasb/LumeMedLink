package com.luismejias.lumemedlink.core.session

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
 */
internal class SessionLock(
    private val inactivityLock: InactivityLock,
    private val unlockGate: UnlockGate,
    private val attempts: FailedAttemptLedger,
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

    suspend fun attemptUnlock(): LockOutcome {
        // Read BEFORE prompting: a budget spent in an earlier process ends the session here, without
        // offering one more try. A count the store cannot read ends it too — fail closed.
        val spent = spentAttempts() ?: return LockOutcome.SessionEnded(SessionEndReason.ATTEMPTS_UNRECORDABLE)
        if (spent >= maxFailedAttempts) return LockOutcome.SessionEnded(SessionEndReason.TOO_MANY_ATTEMPTS)

        return when (unlockGate.unlock()) {
            UnlockOutcome.Unlocked -> {
                // Best effort: a count that survives a success costs the NEXT lock some attempts, which
                // is the safe direction, so a store hiccup here must not keep the doctor out.
                forgetAttempts()
                inactivityLock.unlock()
                LockOutcome.Unlocked
            }

            UnlockOutcome.Cancelled -> LockOutcome.StillLocked(remainingAttempts = null)

            UnlockOutcome.Failed -> {
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
