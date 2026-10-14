package com.luismejias.lumemedlink.features.patient.tabs

import com.luismejias.lumemedlink.core.features.Feature

/**
 * What the patient may do inside the screens (ADR-0060), read from what [com.luismejias.lumemedlink.core.features.
 * FeatureGate] makes available. The host hands a screen no callback for an action that is off, and the screen draws no
 * button for it: a switched-off feature has no door anywhere, not only no tab.
 *
 * An action that leads into another feature needs that one too: booking again goes through «Buscar», so it needs the
 * directory as well as booking.
 */
internal data class PatientActions(
    /** Moving, cancelling and withdrawing an appointment. */
    val changeAppointments: Boolean,
    /** «Buscar un médico» wherever a screen offers it. */
    val search: Boolean,
    /** Booking an hour, from the directory or again from a past appointment. */
    val book: Boolean,
    /** Rating an attention, and «Mis valoraciones». */
    val rate: Boolean,
    /** The bell on «Inicio». */
    val notices: Boolean,
    /** The phone's and the e-mail's pencils. */
    val changeContact: Boolean,
)

internal fun patientActions(available: Set<Feature>): PatientActions = PatientActions(
    changeAppointments = Feature.APPOINTMENT_CHANGES in available,
    search = Feature.DOCTOR_SEARCH in available,
    book = Feature.BOOKING in available && Feature.DOCTOR_SEARCH in available,
    rate = Feature.RATINGS in available,
    notices = Feature.NOTICES in available,
    changeContact = Feature.CONTACT_CHANGE in available,
)

/** [action] when [allowed], else no action at all: the screen then draws no door for it. */
internal fun <T> T.onlyIf(allowed: Boolean): T? = takeIf { allowed }
