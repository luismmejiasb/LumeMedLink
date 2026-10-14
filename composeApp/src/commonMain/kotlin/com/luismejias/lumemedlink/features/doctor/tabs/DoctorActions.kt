package com.luismejias.lumemedlink.features.doctor.tabs

import com.luismejias.lumemedlink.core.features.Feature

/**
 * What the doctor may do inside the screens (ADR-0060), read from what `FeatureGate` makes available: a switched-off
 * feature has no door anywhere, not only no tab.
 */
internal data class DoctorActions(
    /** The requests waiting for an answer, on «Agenda» and as the tab's badge. */
    val answerRequests: Boolean,
    /** The switch that shares a contact channel with every patient. */
    val shareContact: Boolean,
)

internal fun doctorActions(available: Set<Feature>): DoctorActions = DoctorActions(
    answerRequests = Feature.DOCTOR_REQUESTS in available,
    shareContact = Feature.DOCTOR_CONTACT_SHARING in available,
)
