package com.luismejias.lumemedlink.core.features

import com.luismejias.lumemedlink.core.telemetry.RemoteFlag

/** Who an account is, as the server says (ADR-0058): the only thing that opens a feature. */
internal enum class Role { PATIENT, DOCTOR }

/** The role as the app knows it right now. [Unknown] before the server answers, after it fails, and after sign-out. */
internal sealed interface RoleState {
    data object Unknown : RoleState

    data class Known(val role: Role) : RoleState
}

/**
 * Every main feature of the app (ADR-0060). The shell is not here: sign-in, the lock, «Configuración» and «Cerrar
 * sesión» are always there, because a person must always be able to leave.
 */
internal enum class Feature {
    /** «Citas» and the next appointment on «Inicio». */
    APPOINTMENTS,

    /** Cancelling, moving and withdrawing an appointment. */
    APPOINTMENT_CHANGES,

    /** «Buscar»: the directory. */
    DOCTOR_SEARCH,

    /** Booking an hour from the directory. */
    BOOKING,

    /** «Médicos»: the professionals who saw the patient. */
    MY_DOCTORS,

    /** Editing the address on the patient's profile. */
    PROFILE_EDIT,

    /** Changing the phone or the e-mail, with its code. */
    CONTACT_CHANGE,

    /** Rating an attention. */
    RATINGS,

    /** The patient's notices. */
    NOTICES,

    /** The doctor's day. */
    DOCTOR_AGENDA,

    /** The requests waiting for the doctor's answer. */
    DOCTOR_REQUESTS,

    /** The doctor's patients, as an address book. */
    DOCTOR_PATIENTS,

    /** The doctor's switch for sharing a contact channel with patients. */
    DOCTOR_CONTACT_SHARING,
}

/** Who may use a feature, and the flag that can switch it off. */
internal class FeatureRule(val roles: Set<Role>, val flag: RemoteFlag)

/**
 * Each feature's rule, in one exhaustive `when`: a new [Feature] does not compile until it says who may use it and
 * which flag switches it.
 */
internal fun ruleOf(feature: Feature): FeatureRule = when (feature) {
    Feature.APPOINTMENTS -> patient(RemoteFlag.FEATURE_APPOINTMENTS)
    Feature.APPOINTMENT_CHANGES -> patient(RemoteFlag.FEATURE_APPOINTMENT_CHANGES)
    Feature.DOCTOR_SEARCH -> patient(RemoteFlag.FEATURE_DOCTOR_SEARCH)
    Feature.BOOKING -> patient(RemoteFlag.FEATURE_BOOKING)
    Feature.MY_DOCTORS -> patient(RemoteFlag.FEATURE_MY_DOCTORS)
    Feature.PROFILE_EDIT -> patient(RemoteFlag.FEATURE_PROFILE_EDIT)
    Feature.CONTACT_CHANGE -> patient(RemoteFlag.FEATURE_CONTACT_CHANGE)
    Feature.RATINGS -> patient(RemoteFlag.FEATURE_RATINGS)
    Feature.NOTICES -> patient(RemoteFlag.FEATURE_NOTICES)
    Feature.DOCTOR_AGENDA -> doctor(RemoteFlag.FEATURE_DOCTOR_AGENDA)
    Feature.DOCTOR_REQUESTS -> doctor(RemoteFlag.FEATURE_DOCTOR_REQUESTS)
    Feature.DOCTOR_PATIENTS -> doctor(RemoteFlag.FEATURE_DOCTOR_PATIENTS)
    Feature.DOCTOR_CONTACT_SHARING -> doctor(RemoteFlag.FEATURE_DOCTOR_CONTACT_SHARING)
}

private fun patient(flag: RemoteFlag) = FeatureRule(setOf(Role.PATIENT), flag)

private fun doctor(flag: RemoteFlag) = FeatureRule(setOf(Role.DOCTOR), flag)

/**
 * The one decision (ADR-0060 §2): the role opens, the flag closes. An unknown role opens nothing, and a flag never
 * opens a feature its role does not have. [flagOn] answers a flag's current value (its compiled default when Remote
 * Config has not answered).
 */
internal fun isAvailable(feature: Feature, role: RoleState, flagOn: (RemoteFlag) -> Boolean): Boolean {
    val rule = ruleOf(feature)
    return role is RoleState.Known && role.role in rule.roles && flagOn(rule.flag)
}
