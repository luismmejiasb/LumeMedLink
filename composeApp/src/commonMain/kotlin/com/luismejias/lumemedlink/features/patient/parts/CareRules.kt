package com.luismejias.lumemedlink.features.patient.parts

import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.shared.CalendarDate

private val CLOSED = setOf(AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED)

/**
 * Still ahead: from today on and not closed, soonest first. A cancelled appointment is never "upcoming", and
 * one already attended is past even if it was today.
 */
internal fun List<PatientAppointment>.upcoming(today: CalendarDate): List<PatientAppointment> =
    filter { it.date >= today && it.status !in CLOSED }.sortedWith(compareBy({ it.date }, { it.start }))

/** What is behind and was not cancelled — attended, or its day gone by — most recent first. */
internal fun List<PatientAppointment>.past(today: CalendarDate): List<PatientAppointment> {
    val ahead = upcoming(today).toSet()
    return filterNot { it in ahead || it.status == AppointmentStatus.CANCELLED }.mostRecentFirst()
}

/** Mis citas' third tab (e8, spec a4): every cancelled appointment, whoever cancelled it, most recent first. */
internal fun List<PatientAppointment>.cancelled(): List<PatientAppointment> =
    filter { it.status == AppointmentStatus.CANCELLED }.mostRecentFirst()

private fun List<PatientAppointment>.mostRecentFirst(): List<PatientAppointment> =
    sortedWith(compareByDescending<PatientAppointment> { it.date }.thenByDescending { it.start })

/** Requests the practice has not answered yet: the tab's badge and Inicio's "Esperando confirmación". */
internal fun List<PatientAppointment>.awaitingConfirmation(today: CalendarDate): List<PatientAppointment> =
    upcoming(today).filter { it.status == AppointmentStatus.REQUESTED }
