package com.luismejias.lumemedlink.features.auth.flow

/**
 * Every screen of the sign-in flow, mirrored from LumeMed's `AppRoute` (its ADR-0014 and ADR-0022): one
 * route per screen, rendered one at a time inside the focal card.
 *
 * Not here: LumeMed's support screen (there is no support channel for this app yet — the link is not
 * drawn rather than drawn to nowhere) and its trusted-device window (ADR-0024 there): skipping the second
 * factor for thirty days is a session policy this repo has not decided.
 */
internal enum class AuthRoute {
    LOGIN,
    MFA_CHALLENGE,
    TOTP_ENROLLMENT,
    TOTP_ENROLLMENT_CONFIRM,
    BIOMETRIC_ENROLLMENT,
    PASSWORD_RECOVERY,
    PASSWORD_RESET_CODE,
    PASSWORD_RESET_NEW_PASSWORD,
    ;

    /**
     * Whether a back control is offered. Exhaustive with no `else`, as LumeMed's: a new route stops
     * compiling until somebody decides. No back where the step behind is SPENT — a password already used to
     * reach the code, an enrollment already started, a reset already verified.
     */
    val allowsBack: Boolean
        get() = when (this) {
            LOGIN -> false
            MFA_CHALLENGE -> false
            TOTP_ENROLLMENT -> false
            TOTP_ENROLLMENT_CONFIRM -> true
            BIOMETRIC_ENROLLMENT -> false
            PASSWORD_RECOVERY -> true
            PASSWORD_RESET_CODE -> true
            PASSWORD_RESET_NEW_PASSWORD -> false
        }
}

/** Which way the last move went, so the host animates forward and backward differently (LumeMed ADR-0022). */
internal enum class AuthMove { FORWARD, BACKWARD }
