package com.luismejias.lumemedlink.core.session

/**
 * What an unlock attempt produced. The distinction between [Cancelled] and [Failed] is NOT
 * cosmetic: dismissing the prompt is not a wrong finger, and counting it as one would log a doctor
 * out for putting the phone down (mirror of LumeMed's ADR-0020, whose regression this taxonomy
 * exists to prevent). [Invalidated] and [Unavailable] are terminal on purpose — see [SessionLock].
 */
internal sealed interface UnlockOutcome {
    /** The OS released the tier-2 key material. This is the ONLY success — never a boolean. */
    data object Unlocked : UnlockOutcome

    /** The user dismissed the prompt. Does NOT count as a failed attempt. */
    data object Cancelled : UnlockOutcome

    /** The biometric did not match, or the OS reported an authentication failure. Counts. */
    data object Failed : UnlockOutcome

    /**
     * The key material is gone because the enrolled biometrics changed. That is the property the
     * tier is paid for (ADR-0005): a newly enrolled face must NOT inherit the previous session.
     */
    data object Invalidated : UnlockOutcome

    /** No usable biometric hardware/enrollment. Fail closed — there is no way back in. */
    data object Unavailable : UnlockOutcome
}

/**
 * The tier-2 gate of ADR-0005: **biometric re-auth anchored to key material, never a boolean.**
 *
 * Why that phrasing is the whole security claim: a gate that asks "did they authenticate? yes/no"
 * is one patched byte away from always saying yes. Here, unlocking means the operating system
 * actually released a hardware-held secret that it refuses to release without a fresh biometric
 * match — so the check cannot be answered by flipping a flag in app memory.
 *
 * The tier-1 token store is deliberately NOT behind this gate (silent refresh must read without a
 * prompt, ADR-0005); this gate governs re-ENTRY to the app after inactivity, which is the
 * shared-device threat (T2) this repo ranks first.
 */
internal interface UnlockGate {
    /**
     * Creates the tier-2 material for a new session. Returns false when the platform cannot host
     * it (no biometrics enrolled, no hardware) — the caller then knows re-entry will be impossible
     * and must decide up front, not at the locked screen.
     */
    suspend fun enroll(): Boolean

    /** Prompts the user and attempts to recover the material. */
    suspend fun unlock(): UnlockOutcome

    /** Destroys the tier-2 material. Part of the logout wipe contract (§8.13). */
    suspend fun clear()
}
