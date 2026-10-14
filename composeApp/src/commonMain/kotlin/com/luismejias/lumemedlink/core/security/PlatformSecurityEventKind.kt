package com.luismejias.lumemedlink.core.security

import com.luismejias.lumemedlink.core.networking.contract.ReportSecurityEventDtoKind

/**
 * Translates one of this app's kinds into the platform's.
 *
 * The platform's vocabulary is [ReportSecurityEventDtoKind], generated from the contract (ADR-0061). It used to be a
 * hand-transcribed enum here; generating it means a kind the platform renames or drops stops compiling, instead of
 * answering 400 in silence. It is not ours, and this app does not get to extend it. It stays a separate type from
 * [SecurityEventKind], because the two answer to different owners: ours says what THIS app detected (ADR-0023), and
 * the platform's changes when the platform says so.
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
internal fun SecurityEventKind.toPlatformKind(): ReportSecurityEventDtoKind = when (this) {
    // Exact: the tier-2 gate refused an unlock attempt.
    SecurityEventKind.SESSION_UNLOCK_FAILED -> ReportSecurityEventDtoKind.REAUTH_FAILURE

    // Close enough to be TRUE, which is the bar: the session ended because re-entry was impossible
    // — attempts exhausted or no biometrics at all. That is what being locked out of reauth means.
    SecurityEventKind.SESSION_ENDED_UNRECOVERABLE -> ReportSecurityEventDtoKind.REAUTH_LOCKOUT

    // Exact: a stored secret could not be read back.
    SecurityEventKind.SECURE_STORE_UNREADABLE -> ReportSecurityEventDtoKind.SECURITY_STORAGE_FAILURE

    // Exact, since 0.101.0: the names the platform gave what this app detects.
    SecurityEventKind.SESSION_UNLOCK_INVALIDATED -> ReportSecurityEventDtoKind.BIOMETRIC_KEY_INVALIDATED
    SecurityEventKind.NETWORK_ORIGIN_REFUSED -> ReportSecurityEventDtoKind.FOREIGN_ORIGIN_BLOCKED
    SecurityEventKind.NETWORK_INTERSTITIAL_DETECTED -> ReportSecurityEventDtoKind.UNEXPECTED_HTML_RESPONSE
}
