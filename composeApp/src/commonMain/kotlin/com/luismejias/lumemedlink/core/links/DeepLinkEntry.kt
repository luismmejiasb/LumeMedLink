package com.luismejias.lumemedlink.core.links

/**
 * Who a destination belongs to. A link for the other role is dropped, never re-aimed (ADR-0053 §4).
 */
internal enum class LinkAudience { PATIENT, DOCTOR, ANY }

/**
 * The one value a link may carry, always in the query and always an [com.luismejias.lumemedlink.shared.OpaqueRef]
 * (ADR-0053 §2): an id that means nothing to whoever reads the URL or the lock screen.
 */
internal enum class LinkParam(val queryName: String) {
    APPOINTMENT("cita"),
    NOTICE("aviso"),
}

/**
 * Every place a link or a push can open, one entry each (ADR-0053). The path IS the identity, matched exactly; values
 * travel in the query. Paths are Spanish because the people reading them are (the author's call, 2026-10-13).
 *
 * The metadata below is declared by exhaustive `when`s with no `else`, so a new entry does not compile until somebody
 * has decided who it is for, what it needs and whether a push may carry it. The hosts map entries to screens with
 * exhaustive `when`s too: an entry nobody can open cannot be added by accident.
 *
 * Paths are published: once a link is out in a message, renaming its path breaks it. `DeepLinkCatalogTest` pins each
 * one by hand for that reason.
 */
internal enum class DeepLinkEntry(val path: String) {
    // ── The patient ─────────────────────────────────────────────────────────────────────────────
    HOME("inicio"),
    SEARCH("buscar"),
    APPOINTMENTS("citas"),
    APPOINTMENT_DETAIL("citas/detalle"),
    APPOINTMENT_RATING("citas/valorar"),
    MY_DOCTORS("medicos"),
    SETTINGS("configuracion"),
    SETTINGS_PROFILE("configuracion/mis-datos"),
    SETTINGS_REMINDERS("configuracion/recordatorios"),
    SETTINGS_RATINGS("configuracion/valoraciones"),
    SETTINGS_COMPLAINTS("configuracion/reclamos"),
    SETTINGS_PRIVACY("configuracion/privacidad"),

    // ── Official communications, for either role ────────────────────────────────────────────────
    /** The notice centre behind Inicio's bell. NOT DESIGNED yet: the hosts land on their home until it is. */
    NOTICES("avisos"),
    NOTICE_DETAIL("avisos/detalle"),

    // ── The doctor ──────────────────────────────────────────────────────────────────────────────
    DOCTOR_AGENDA("medico/agenda"),
    DOCTOR_PATIENTS("medico/pacientes"),
    DOCTOR_SETTINGS("medico/configuracion"),
    ;

    val audience: LinkAudience
        get() = when (this) {
            HOME, SEARCH, APPOINTMENTS, APPOINTMENT_DETAIL, APPOINTMENT_RATING, MY_DOCTORS, SETTINGS,
            SETTINGS_PROFILE, SETTINGS_REMINDERS, SETTINGS_RATINGS, SETTINGS_COMPLAINTS, SETTINGS_PRIVACY,
            -> LinkAudience.PATIENT
            NOTICES, NOTICE_DETAIL -> LinkAudience.ANY
            DOCTOR_AGENDA, DOCTOR_PATIENTS, DOCTOR_SETTINGS -> LinkAudience.DOCTOR
        }

    /** The value this entry cannot open without; a link missing it, or carrying a malformed one, is refused. */
    val requires: LinkParam?
        get() = when (this) {
            APPOINTMENT_DETAIL, APPOINTMENT_RATING -> LinkParam.APPOINTMENT
            NOTICE_DETAIL -> LinkParam.NOTICE
            HOME, SEARCH, APPOINTMENTS, MY_DOCTORS, SETTINGS, SETTINGS_PROFILE, SETTINGS_REMINDERS, SETTINGS_RATINGS,
            SETTINGS_COMPLAINTS, SETTINGS_PRIVACY, NOTICES, DOCTOR_AGENDA, DOCTOR_PATIENTS, DOCTOR_SETTINGS,
            -> null
        }

    /**
     * Whether a push may carry this entry. Search and the privacy switch are things a person goes to, not things the
     * server sends them to: a notification that opens the analytics consent would be a nudge, and §8.5 keeps push to
     * what the person needs to know.
     */
    val pushable: Boolean
        get() = when (this) {
            HOME, APPOINTMENTS, APPOINTMENT_DETAIL, APPOINTMENT_RATING, MY_DOCTORS, SETTINGS, SETTINGS_PROFILE,
            SETTINGS_REMINDERS, SETTINGS_RATINGS, SETTINGS_COMPLAINTS, NOTICES, NOTICE_DETAIL, DOCTOR_AGENDA,
            DOCTOR_PATIENTS, DOCTOR_SETTINGS,
            -> true
            SEARCH, SETTINGS_PRIVACY -> false
        }

    companion object {
        /** The entry whose path is exactly [path], already normalised (no leading or trailing slash). */
        fun matching(path: String): DeepLinkEntry? = entries.firstOrNull { it.path == path }
    }
}
