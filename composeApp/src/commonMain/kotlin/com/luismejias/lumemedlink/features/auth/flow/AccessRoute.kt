package com.luismejias.lumemedlink.features.auth.flow

/**
 * Every screen of the access flow, one per design id (03-Specs/1-Auth.md). An error that keeps the person on
 * the same screen (1b-e1, 2a-e1, 2c-e1…) is that screen's state, not a route; one that replaces the screen
 * (1b-e2, 2c-e2, 4a-e1, 4b-e1, 1c-e1) is a route.
 *
 * Waiting, and so absent (ADR-0044): the QR scanner and its camera state 4a-e2.
 */
internal enum class AccessRoute {
    /** 1a — the kit's carousel is pending; until then the three entry actions only. */
    WELCOME,

    /** 1b, with 1b-e1 and 1b-e3 as its state. */
    SIGN_IN,

    /** 1b-e2. */
    LOCKED_OUT,

    /** 1c. */
    QUICK_SIGN_IN,

    /** 1c-e1. */
    QUICK_SIGN_IN_UNAVAILABLE,

    /** 2a, with 2a-e1 and 2a-e2 as its state. */
    SIGN_UP_PROFILE,

    /** 2b, with 2b-e1. */
    SIGN_UP_CREDENTIALS,

    /** 2c, with 2c-e1. */
    SIGN_UP_CODE,

    /** 2c-e2. */
    SIGN_UP_CODE_EXPIRED,

    /** 2d. */
    SIGN_UP_DONE,

    /** 3a. */
    RECOVERY_RUT,

    /** 3b. */
    RECOVERY_CODE,

    /** 3c. */
    RECOVERY_NEW_PASSWORD,

    /** 3d. */
    RECOVERY_DONE,

    /** 4a — the typed code only. */
    DOCTOR_CODE,

    /** 4a-e1. */
    DOCTOR_CODE_SPENT,

    /** 4b. */
    DOCTOR_CONFIRM,

    /** 4b-e1. */
    DOCTOR_UNVERIFIED,

    /** 4c. */
    DOCTOR_LINKED,
    ;

    /**
     * Whether the back disc is drawn — the design's own choice per screen. Exhaustive with no `else`: a new
     * route stops compiling until somebody decides. No back on an outcome (a lockout, an account created, a
     * link made) or on a step whose predecessor is spent.
     */
    val allowsBack: Boolean
        get() = when (this) {
            WELCOME -> false
            SIGN_IN -> true
            LOCKED_OUT -> false
            QUICK_SIGN_IN -> false
            QUICK_SIGN_IN_UNAVAILABLE -> false
            SIGN_UP_PROFILE -> true
            SIGN_UP_CREDENTIALS -> true
            SIGN_UP_CODE -> true
            SIGN_UP_CODE_EXPIRED -> true
            SIGN_UP_DONE -> false
            RECOVERY_RUT -> true
            RECOVERY_CODE -> true
            RECOVERY_NEW_PASSWORD -> true
            RECOVERY_DONE -> false
            DOCTOR_CODE -> true
            DOCTOR_CODE_SPENT -> true
            DOCTOR_CONFIRM -> true
            DOCTOR_UNVERIFIED -> false
            DOCTOR_LINKED -> false
        }
}

/** Which way the last move went, so the host animates forward and backward differently. */
internal enum class AccessMove { FORWARD, BACKWARD }

/** Who came in. The flow ends here; the main app for each role is not built yet. */
internal enum class AccessRole { PATIENT, DOCTOR }
