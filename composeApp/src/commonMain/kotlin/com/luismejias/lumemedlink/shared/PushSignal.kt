package com.luismejias.lumemedlink.shared

import kotlin.jvm.JvmInline

/** Bounds for an opaque reference: long enough to be a real id, short enough to hold no sentence. */
private const val OPAQUE_REF_MIN_LENGTH = 8
private const val OPAQUE_REF_MAX_LENGTH = 64
private val OPAQUE_REF_SHAPE = Regex("^[A-Za-z0-9_-]+$")

/**
 * An identifier that carries no meaning to a reader — the only kind of reference allowed to travel
 * in a push payload or appear on a pre-auth surface (ADR-0012).
 *
 * It validates on construction, and that is the whole point: "Control de diabetes del paciente
 * Pérez" cannot become an `OpaqueRef`, because spaces and accents are not in the accepted shape and
 * the length ceiling refuses a sentence. A future slice that tries to smuggle content through this
 * field gets `null`, not a leak.
 */
@JvmInline
internal value class OpaqueRef private constructor(val value: String) {
    override fun toString(): String = "OpaqueRef(<redacted>)"

    companion object {
        fun parseOrNull(raw: String): OpaqueRef? = when {
            raw.length !in OPAQUE_REF_MIN_LENGTH..OPAQUE_REF_MAX_LENGTH -> null
            !OPAQUE_REF_SHAPE.matches(raw) -> null
            else -> OpaqueRef(raw)
        }
    }
}

/**
 * What a push notification is allowed to say. A closed set of reasons to wake the app — never a
 * subject, never a name, never a clinical word.
 *
 * Each kind maps to a FIXED, generic, localized string chosen by the app; the payload never
 * supplies display text, so the server cannot decide what appears on a locked screen.
 */
internal enum class PushSignalKind {
    /** One of the patient's appointments is coming up. Which, and when, is read inside the app. */
    APPOINTMENT_REMINDER,

    /** The clinic confirmed, moved or cancelled one of the patient's appointments. Not which, not how. */
    APPOINTMENT_UPDATED,

    /** A visit can be rated now. Not with whom. */
    RATING_INVITED,

    /** An official communication from the clinic or from LumeMedLink is waiting in the notice centre. */
    OFFICIAL_NOTICE,

    /** The account changed — its details, a setting. The person should hear of it, and act if it was not them. */
    ACCOUNT_CHANGED,

    /** Something in the doctor's agenda changed. Which appointment is fetched authenticated. */
    AGENDA_CHANGED,

    /** The session is no longer valid; the app should re-authenticate. */
    SESSION_REVOKED,

    /** Something is waiting in the app. Deliberately vague: vagueness is the feature. */
    SOMETHING_WAITING,
}

/**
 * The ONLY shape a push payload may take in this app (ADR-0012, threat model T2).
 *
 * Why this type exists instead of a rule in a document: the leak this prevents is a single line of
 * a future slice — `setContentText(payload.message)` — and by then the payload would already be
 * carrying "recordatorio de tu control de diabetes". Here there IS no message field. The wrong
 * thing is not forbidden by policy; it is impossible to write down.
 *
 * The user-visible text comes from [kind] alone, through the app's own localized strings. The
 * actual content is fetched, authenticated, INSIDE the app after unlocking (§8.5).
 */
internal data class PushSignal(val kind: PushSignalKind, val ref: OpaqueRef? = null) {
    /**
     * The key of the fixed, generic copy shown for this signal. Names a string the APP owns, never
     * text that arrived over the network.
     */
    val displayCopyKey: String
        get() = when (kind) {
            PushSignalKind.APPOINTMENT_REMINDER -> "push.appointment_reminder"
            PushSignalKind.APPOINTMENT_UPDATED -> "push.appointment_updated"
            PushSignalKind.RATING_INVITED -> "push.rating_invited"
            PushSignalKind.OFFICIAL_NOTICE -> "push.official_notice"
            PushSignalKind.ACCOUNT_CHANGED -> "push.account_changed"
            PushSignalKind.AGENDA_CHANGED -> "push.agenda_changed"
            PushSignalKind.SESSION_REVOKED -> "push.session_revoked"
            PushSignalKind.SOMETHING_WAITING -> "push.something_waiting"
        }
}
