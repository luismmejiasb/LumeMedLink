package com.luismejias.lumemedlink.core.links

import com.luismejias.lumemedlink.shared.PushSignal
import com.luismejias.lumemedlink.shared.PushSignalKind

/** The two keys a push payload may hold (ADR-0012, ADR-0053 §5). Anything else in the payload is ignored. */
internal const val PUSH_KIND_KEY = "kind"
internal const val PUSH_LINK_KEY = "link"

/**
 * A push, read: what it is (its fixed, app-owned copy comes from [signal]) and where tapping it goes. [link] is null
 * only for a signal that opens nothing, like a revoked session.
 */
internal data class PushNotice(val signal: PushSignal, val link: DeepLink?)

/**
 * Where a signal may send the person (ADR-0053 §5). The copy says "recordatorio de cita", so the tap cannot open the
 * privacy switch: the destination is held to the kind, and a link outside it falls back to the kind's own place.
 */
internal fun PushSignalKind.allows(entry: DeepLinkEntry): Boolean = entry.pushable &&
    when (this) {
        PushSignalKind.APPOINTMENT_REMINDER, PushSignalKind.APPOINTMENT_UPDATED ->
            entry == DeepLinkEntry.APPOINTMENTS || entry == DeepLinkEntry.APPOINTMENT_DETAIL
        PushSignalKind.RATING_INVITED -> entry == DeepLinkEntry.APPOINTMENT_RATING || entry == DeepLinkEntry.HOME
        PushSignalKind.OFFICIAL_NOTICE -> entry == DeepLinkEntry.NOTICES || entry == DeepLinkEntry.NOTICE_DETAIL
        PushSignalKind.ACCOUNT_CHANGED -> entry.path.startsWith("configuracion") ||
            entry == DeepLinkEntry.DOCTOR_SETTINGS
        PushSignalKind.AGENDA_CHANGED -> entry.audience == LinkAudience.DOCTOR
        PushSignalKind.SOMETHING_WAITING -> true
        PushSignalKind.SESSION_REVOKED -> false
    }

/** Where a signal goes when its payload names no destination, or one it may not use. */
internal val PushSignalKind.defaultEntry: DeepLinkEntry?
    get() = when (this) {
        PushSignalKind.APPOINTMENT_REMINDER, PushSignalKind.APPOINTMENT_UPDATED -> DeepLinkEntry.APPOINTMENTS
        PushSignalKind.RATING_INVITED -> DeepLinkEntry.HOME
        PushSignalKind.OFFICIAL_NOTICE -> DeepLinkEntry.NOTICES
        PushSignalKind.ACCOUNT_CHANGED -> DeepLinkEntry.SETTINGS
        PushSignalKind.AGENDA_CHANGED -> DeepLinkEntry.DOCTOR_AGENDA
        PushSignalKind.SOMETHING_WAITING -> DeepLinkEntry.HOME
        // The app re-checks the session; a tap opens it where it is.
        PushSignalKind.SESSION_REVOKED -> null
    }

/**
 * Reads a data-only payload (ADR-0038 §3): `kind` and, optionally, `link`. An unknown kind is no notice at all; a link
 * that is malformed, outside the catalog or outside the kind falls back to the kind's own place, so a server can widen
 * nothing by writing a different link.
 */
internal fun pushNoticeOf(data: Map<String, String>): PushNotice? {
    val kind = PushSignalKind.entries.firstOrNull { it.name == data[PUSH_KIND_KEY] } ?: return null
    val named = data[PUSH_LINK_KEY]?.let(::parseRelativeLink)?.takeIf { kind.allows(it.entry) }
    val link = named ?: kind.defaultEntry?.let { DeepLink(it) }
    return PushNotice(PushSignal(kind), link)
}
