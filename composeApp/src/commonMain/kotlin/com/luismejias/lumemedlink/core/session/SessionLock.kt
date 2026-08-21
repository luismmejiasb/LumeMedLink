package com.luismejias.lumemedlink.core.session

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
 * - Wrong biometrics count, and enough of them end the session rather than allow infinite tries.
 * - Enrollment changed or biometrics unavailable end the session immediately: in both cases
 *   re-entry is impossible or would be inherited by a new identity, and a lock that cannot tell
 *   whether it should open, stays closed.
 */
internal class SessionLock(
    private val inactivityLock: InactivityLock,
    private val unlockGate: UnlockGate,
    private val maxFailedAttempts: Int = DEFAULT_MAX_FAILED_ATTEMPTS,
) {
    private var failedAttempts = 0

    fun isLocked(): Boolean = inactivityLock.isLocked()

    /** Slides the inactivity window. Cannot unlock — only a real re-auth can (see [InactivityLock]). */
    fun recordActivity() {
        inactivityLock.recordActivity()
    }

    /** Opens the window after a successful login, and clears any attempt history. */
    fun sessionEstablished() {
        failedAttempts = 0
        inactivityLock.unlock()
    }

    /** Returns to the born-locked state. Part of logout. */
    fun sessionEnded() {
        failedAttempts = 0
        inactivityLock.reset()
    }

    suspend fun attemptUnlock(): LockOutcome = when (unlockGate.unlock()) {
        UnlockOutcome.Unlocked -> {
            failedAttempts = 0
            inactivityLock.unlock()
            LockOutcome.Unlocked
        }

        UnlockOutcome.Cancelled -> LockOutcome.StillLocked(remainingAttempts = null)

        UnlockOutcome.Failed -> {
            failedAttempts += 1
            if (failedAttempts >= maxFailedAttempts) {
                LockOutcome.SessionEnded(SessionEndReason.TOO_MANY_ATTEMPTS)
            } else {
                LockOutcome.StillLocked(remainingAttempts = maxFailedAttempts - failedAttempts)
            }
        }

        UnlockOutcome.Invalidated -> LockOutcome.SessionEnded(SessionEndReason.ENROLLMENT_CHANGED)

        UnlockOutcome.Unavailable -> LockOutcome.SessionEnded(SessionEndReason.BIOMETRICS_UNAVAILABLE)
    }
}
