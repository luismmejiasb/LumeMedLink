package com.luismejias.lumemedlink.core.security

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The platform's OWN closed vocabulary for `POST /v1/security-events`, transcribed from contract
 * 0.32.0. It is not ours and this app does not get to extend it: a value outside this set is a 400.
 *
 * Why it is a separate type from [SecurityEventKind] instead of renaming ours: the two vocabularies
 * answer to different owners. Ours describes what THIS app detected and is governed by ADR-0023;
 * this one is the platform's wire contract and changes when the platform says so. Collapsing them
 * would mean a backend rename silently rewriting our internal semantics, and it would hide the fact
 * that some of what this app can detect **has no name over there** — which is the finding, not a
 * detail.
 *
 * Entries are UPPER_SNAKE with an explicit `@SerialName` rather than camelCase entries: the wire
 * spelling is then written down once, visible, and impossible to change by accident with a rename.
 */
@Serializable
internal enum class PlatformSecurityEventKind {
    @SerialName("integrityViolation")
    INTEGRITY_VIOLATION,

    @SerialName("screenCaptureDetected")
    SCREEN_CAPTURE_DETECTED,

    @SerialName("reauthFailure")
    REAUTH_FAILURE,

    @SerialName("reauthLockout")
    REAUTH_LOCKOUT,

    @SerialName("forcedUpdateShown")
    FORCED_UPDATE_SHOWN,

    @SerialName("killSwitchTriggered")
    KILL_SWITCH_TRIGGERED,

    @SerialName("suspiciousLoginReset")
    SUSPICIOUS_LOGIN_RESET,

    @SerialName("securityStorageFailure")
    SECURITY_STORAGE_FAILURE,
}

/**
 * Translates one of this app's kinds into the platform's, or answers `null` when the platform has
 * **no name for it**.
 *
 * ## Why this function exists at all
 *
 * `HttpSecurityEventReporter` used to serialize [SecurityEventKind] straight onto the wire, with the
 * field name guessed and the vocabulary never checked — `backend-requests/0004` asked for the schema
 * and nothing came back, so it shipped on an assumption. Reading contract 0.32.0 settled it: the
 * field name `kind` was right and **every value was wrong**. Since the reporter never throws by
 * doctrine, the whole channel would have answered 400 to every event **in silence** — which is,
 * word for word, the failure ADR-0023 was written to avoid after LumeMed shipped this channel wired
 * to a no-op and the platform never received an event.
 *
 * ## Why `null` instead of a nearest neighbour
 *
 * Three of this app's six kinds have no home in the platform's set, and each near-miss would be a
 * lie with consequences:
 *
 * - `SESSION_UNLOCK_INVALIDATED` is not a `securityStorageFailure`. The tier-2 key was destroyed by
 *   a new biometric enrollment — that is the control **working as designed**, and filing it as a
 *   storage failure raises an alarm for a healthy device.
 * - `NETWORK_ORIGIN_REFUSED` is not an `integrityViolation`. That name means the binary or the
 *   runtime was tampered with; this is our own stack refusing to send somewhere, which says nothing
 *   about the device.
 * - `NETWORK_INTERSTITIAL_DETECTED` is likewise not an integrity event.
 *
 * The kind IS the message on this channel (ADR-0023). Sending an approximately-true kind is worse
 * than sending nothing, because the platform cannot tell the difference and will act on it.
 *
 * These three are exactly the client-side detections this app added and the platform's vocabulary —
 * written for LumeMed — never anticipated. `backend-requests/0006` asks for them by name.
 *
 * The `when` is exhaustive with **no `else`**: adding a kind to [SecurityEventKind] stops compiling
 * until somebody decides what the platform should hear, which is the decision this file exists to
 * force.
 */
internal fun SecurityEventKind.toPlatformKind(): PlatformSecurityEventKind? = when (this) {
    // Exact: the tier-2 gate refused an unlock attempt.
    SecurityEventKind.SESSION_UNLOCK_FAILED -> PlatformSecurityEventKind.REAUTH_FAILURE

    // Close enough to be TRUE, which is the bar: the session ended because re-entry was impossible
    // — attempts exhausted or no biometrics at all. That is what being locked out of reauth means.
    SecurityEventKind.SESSION_ENDED_UNRECOVERABLE -> PlatformSecurityEventKind.REAUTH_LOCKOUT

    // Exact: a stored secret could not be read back.
    SecurityEventKind.SECURE_STORE_UNREADABLE -> PlatformSecurityEventKind.SECURITY_STORAGE_FAILURE

    // No name over there. See the KDoc — each of these has a tempting near-miss that would be false.
    SecurityEventKind.SESSION_UNLOCK_INVALIDATED -> null
    SecurityEventKind.NETWORK_ORIGIN_REFUSED -> null
    SecurityEventKind.NETWORK_INTERSTITIAL_DETECTED -> null
}
