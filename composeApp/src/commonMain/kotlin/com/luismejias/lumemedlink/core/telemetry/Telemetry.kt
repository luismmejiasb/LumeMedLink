package com.luismejias.lumemedlink.core.telemetry

import androidx.compose.runtime.Composable

/**
 * Firebase's reach into this app, closed (ADR-0052): Crashlytics and Analytics behind one consent switch, Analytics
 * through a catalog, Remote Config through a catalog. A feature never touches Firebase; it gets one of the three narrow
 * faces below, never this whole (ADR-0060 §3): the consent needs no flags, and the feature gate needs no analytics.
 */
internal interface Telemetry :
    TelemetryCollection,
    AnalyticsLog,
    RemoteFlags

/** Whether Crashlytics and Analytics collect: the one face the person's consent drives. */
internal interface TelemetryCollection {
    /**
     * Crashlytics and Analytics together, as the person's one consent governs both (ADR-0052 §2). Turning them off
     * also deletes what the device still holds: Crashlytics' unsent reports and Analytics' local data.
     */
    fun setCollectionEnabled(enabled: Boolean)
}

/** Analytics, through its catalog only. */
internal interface AnalyticsLog {
    /**
     * One event of the catalog. There is no other way in: no user ID, no properties, and no free parameter (§3). A
     * detail's key is an [AnalyticsParam] and its value an enum constant of this code — never a string from data.
     */
    fun log(event: AnalyticsEvent, details: Map<AnalyticsParam, Enum<*>> = emptyMap())
}

/** Remote Config, through its catalog only. */
internal interface RemoteFlags {
    /** The flag's remote value once fetched, its default until then, offline, or without configuration (§5). */
    fun flag(flag: RemoteFlag): Boolean

    /** Fetches and activates Remote Config's values for the next read. Fails into the defaults, silently. */
    suspend fun refreshFlags()
}

/**
 * What Analytics may say (ADR-0052 §3): that something happened, never to whom or with whom. An entry never names a
 * doctor, a specialty, a clinic, a date, a place or a reason — the payload is where GoodRx's leak was, not the SDK.
 * A new entry is a decision about what leaves for Google, so it gets reviewed like a dependency.
 */
internal enum class AnalyticsEvent(val eventName: String) {
    /** The access flow ended in an account (2c). */
    SIGN_UP_COMPLETED("sign_up_completed"),

    /** The access flow ended in a sign-in. */
    SIGN_IN_COMPLETED("sign_in_completed"),

    /** An hour was booked (a2); not with whom, not when, not where. */
    APPOINTMENT_BOOKED("appointment_booked"),

    /** An appointment moved to another hour (f3); not which one. */
    APPOINTMENT_RESCHEDULED("appointment_rescheduled"),

    /** An appointment was cancelled or a request withdrawn (f4); never the reason chosen. */
    APPOINTMENT_CANCELLED("appointment_cancelled"),

    /** A rating was sent (f5); not its stars, not its words, not whom it rates. */
    APPOINTMENT_RATED("appointment_rated"),

    /**
     * A link or a notification opened a screen (ADR-0053): with [AnalyticsParam.DESTINATION], the catalog entry, and
     * [AnalyticsParam.ORIGIN]. Never the id it carried. The author's call, 2026-10-13.
     */
    DEEP_LINK_OPENED("deep_link_opened"),
}

/** The keys a detail may have. Its value is an enum constant's name, lower-cased: code, never data. */
internal enum class AnalyticsParam(val paramName: String) {
    DESTINATION("destination"),
    ORIGIN("origin"),
}

/** What Firebase receives for [details]: each key's name and its enum value's lower-cased name. */
internal fun analyticsValues(details: Map<AnalyticsParam, Enum<*>>): Map<String, String> =
    details.entries.associate { (param, value) -> param.paramName to value.name.lowercase() }

/**
 * What Remote Config may switch (ADR-0052 §5): product flags with a default in code. Never personal data, never the
 * kill switch (§8.15 keeps that on the backend), never a security control.
 *
 * One flag per feature of `core/features` (ADR-0060). A flag can only switch its feature OFF: the role decides who may
 * use it, and a flag never grants a role. Each default is the feature as the binary ships it, so Remote Config not
 * answering leaves the app as compiled. A feature that ships dark starts with `default = false`.
 */
internal enum class RemoteFlag(val key: String, val default: Boolean) {
    FEATURE_APPOINTMENTS("feature_appointments", default = true),
    FEATURE_APPOINTMENT_CHANGES("feature_appointment_changes", default = true),
    FEATURE_DOCTOR_SEARCH("feature_doctor_search", default = true),
    FEATURE_BOOKING("feature_booking", default = true),
    FEATURE_MY_DOCTORS("feature_my_doctors", default = true),
    FEATURE_PROFILE_EDIT("feature_profile_edit", default = true),
    FEATURE_CONTACT_CHANGE("feature_contact_change", default = true),
    FEATURE_RATINGS("feature_ratings", default = true),
    FEATURE_NOTICES("feature_notices", default = true),
    FEATURE_DOCTOR_AGENDA("feature_doctor_agenda", default = true),
    FEATURE_DOCTOR_REQUESTS("feature_doctor_requests", default = true),
    FEATURE_DOCTOR_PATIENTS("feature_doctor_patients", default = true),
    FEATURE_DOCTOR_CONTACT_SHARING("feature_doctor_contact_sharing", default = true),
}

/** Without Firebase configuration (ADR-0052 §8): collects nothing, sends nothing, answers every flag's default. */
internal object UnconfiguredTelemetry : Telemetry {
    override fun setCollectionEnabled(enabled: Boolean) = Unit

    override fun log(event: AnalyticsEvent, details: Map<AnalyticsParam, Enum<*>>) = Unit

    override fun flag(flag: RemoteFlag): Boolean = flag.default

    override suspend fun refreshFlags() = Unit
}

/** The platform's Firebase, or [UnconfiguredTelemetry] when the app was built without its configuration. */
@Composable
internal expect fun rememberTelemetry(): Telemetry
