package com.luismejias.lumemedlink.core.security

/**
 * What this app is allowed to tell the backend happened (§8.16, ADR-0023).
 *
 * A closed set of OPAQUE kinds. The backend's endpoint takes a kind and nothing else, and this type
 * makes that shape unbreakable on our side: there is no field for a message, a user, a device or a
 * timestamp-with-context. A reporting channel that accepts free text is the channel that eventually
 * carries "unlock failed for Dr. Pérez, patient 11111111-1" to a server log.
 *
 * The names describe a CLASS of event, never an instance. `SESSION_UNLOCK_FAILED` says the tier-2
 * gate refused someone; it does not say who, when, or on whose record.
 */
internal enum class SecurityEventKind {
    /** The biometric gate refused an unlock attempt (ADR-0011). */
    SESSION_UNLOCK_FAILED,

    /** The tier-2 key was destroyed by a new biometric enrollment — a device-level change. */
    SESSION_UNLOCK_INVALIDATED,

    /** The session ended because re-entry was impossible: no biometrics, or attempts exhausted. */
    SESSION_ENDED_UNRECOVERABLE,

    /** The stack refused to send a request aimed at an origin other than its own (ADR-0016). */
    NETWORK_ORIGIN_REFUSED,

    /** A stored secret could not be read back — corrupt, or its key was invalidated (ADR-0009). */
    SECURE_STORE_UNREADABLE,

    /** A 2xx response arrived carrying HTML: an interstitial, i.e. something intercepted us. */
    NETWORK_INTERSTITIAL_DETECTED,
}

/**
 * Reports a security event to the backend (§8.16).
 *
 * **Fails silently, by doctrine.** The family's fail-direction table is explicit that this channel
 * "no lanza": every caller is in the middle of applying a protection, and a backend that is down is
 * not their problem. A report that throws would turn an outage into a second failure at the worst
 * possible moment.
 *
 * The HTTP implementation waits on the backend building `ADR-0036`; until then [NoOpSecurityEventReporter]
 * is wired and says so rather than pretending to report.
 */
internal interface SecurityEventReporter {
    suspend fun report(kind: SecurityEventKind)
}

/**
 * The stand-in until the contract call exists. It is NOT a silent discard dressed as a feature:
 * LumeMed shipped exactly that — a security-event channel wired to a no-op — and the platform never
 * received a single event while its table sat verified-and-writable (ecosystem board §3). This type
 * exists under its own honest name so a reader sees the gap instead of assuming the channel works.
 */
internal object NoOpSecurityEventReporter : SecurityEventReporter {
    override suspend fun report(kind: SecurityEventKind) = Unit
}
