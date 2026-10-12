package com.luismejias.lumemedlink.core.security

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The platform's OWN closed vocabulary for `POST /v1/security-events`, transcribed from contract
 * 0.32.0 and grown by three in 0.101.0, when the platform named what this app detects
 * (`backend-requests/0006`). It is not ours and this app does not get to extend it: a value outside
 * this set is a 400, and a server older than 0.101.0 answers 400 to the three new ones.
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

    @SerialName("biometricKeyInvalidated")
    BIOMETRIC_KEY_INVALIDATED,

    @SerialName("foreignOriginBlocked")
    FOREIGN_ORIGIN_BLOCKED,

    @SerialName("unexpectedHtmlResponse")
    UNEXPECTED_HTML_RESPONSE,
}

/**
 * Translates one of this app's kinds into the platform's.
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
 * ## Why every kind has an exact name, and never a nearest neighbour
 *
 * Until contract 0.101.0 three of this app's kinds had no home over there, and they were not sent at
 * all: each near-miss would have been a lie with consequences. A key destroyed by a new biometric
 * enrollment is the control **working**, not a `securityStorageFailure`; our stack refusing an origin,
 * or a 2xx arriving as HTML, says nothing about a tampered binary, so neither is an
 * `integrityViolation`. `backend-requests/0006` asked for the three by name and the platform added
 * them (its answer `0049`). The kind IS the message on this channel (ADR-0023): if a future kind has
 * no name over there, this goes back to answering `null` for it — never to the closest neighbour.
 *
 * The `when` is exhaustive with **no `else`**: adding a kind to [SecurityEventKind] stops compiling
 * until somebody decides what the platform should hear, which is the decision this file exists to
 * force.
 */
internal fun SecurityEventKind.toPlatformKind(): PlatformSecurityEventKind = when (this) {
    // Exact: the tier-2 gate refused an unlock attempt.
    SecurityEventKind.SESSION_UNLOCK_FAILED -> PlatformSecurityEventKind.REAUTH_FAILURE

    // Close enough to be TRUE, which is the bar: the session ended because re-entry was impossible
    // — attempts exhausted or no biometrics at all. That is what being locked out of reauth means.
    SecurityEventKind.SESSION_ENDED_UNRECOVERABLE -> PlatformSecurityEventKind.REAUTH_LOCKOUT

    // Exact: a stored secret could not be read back.
    SecurityEventKind.SECURE_STORE_UNREADABLE -> PlatformSecurityEventKind.SECURITY_STORAGE_FAILURE

    // Exact, since 0.101.0: the names the platform gave what this app detects.
    SecurityEventKind.SESSION_UNLOCK_INVALIDATED -> PlatformSecurityEventKind.BIOMETRIC_KEY_INVALIDATED
    SecurityEventKind.NETWORK_ORIGIN_REFUSED -> PlatformSecurityEventKind.FOREIGN_ORIGIN_BLOCKED
    SecurityEventKind.NETWORK_INTERSTITIAL_DETECTED -> PlatformSecurityEventKind.UNEXPECTED_HTML_RESPONSE
}
