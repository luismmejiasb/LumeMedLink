package com.luismejias.lumemedlink.features.patient.appointments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.AppointmentsSource
import com.luismejias.lumemedlink.core.care.ClinicClock
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.standInPatientCare
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.core.screen.StandIn
import com.luismejias.lumemedlink.core.screen.allOf
import com.luismejias.lumemedlink.core.screen.markLoading
import com.luismejias.lumemedlink.core.screen.ok
import com.luismejias.lumemedlink.features.patient.parts.awaitingConfirmation
import com.luismejias.lumemedlink.features.patient.parts.cancelled
import com.luismejias.lumemedlink.features.patient.parts.past
import com.luismejias.lumemedlink.features.patient.parts.upcoming
import com.luismejias.lumemedlink.shared.CalendarDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class AppointmentsSegment { UPCOMING, PAST, CANCELLED }

/** What Mis citas draws (p5, e8). */
internal data class AppointmentsContent(
    val today: CalendarDate,
    val upcoming: List<PatientAppointment>,
    val past: List<PatientAppointment>,
    val cancelled: List<PatientAppointment>,
    /** "Puedes cambiar o cancelar hasta 24 h antes": the practice's window. */
    val changeWindowHours: Int,
) {
    /** The tab's badge: requests still waiting for the practice. */
    val awaiting: Int get() = upcoming.awaitingConfirmation(today).size

    override fun toString(): String =
        "AppointmentsContent(upcoming=${upcoming.size}, past=${past.size}, cancelled=${cancelled.size})"
}

/** Mis citas' owner (ADR-0045). Its count also feeds the tab bar's badge. */
internal class AppointmentsViewModel(private val clock: ClinicClock, private val appointments: AppointmentsSource) :
    ViewModel() {
    private val current = MutableStateFlow<CareLoad<AppointmentsContent>>(CareLoad.Loading)
    val state: StateFlow<CareLoad<AppointmentsContent>> = current.asStateFlow()

    /** The screen's own shape while it waits (ADR-0055). */
    private val standInContent = StandIn<AppointmentsContent>()
    val standIn: StateFlow<AppointmentsContent?> = standInContent.content

    private val shown = MutableStateFlow(AppointmentsSegment.UPCOMING)
    val segment: StateFlow<AppointmentsSegment> = shown.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun segmentChosen(segment: AppointmentsSegment) {
        shown.value = segment
    }

    suspend fun load() {
        standInContent.fill { standInPatientCare().let { read(it, it) } }
        current.markLoading()
        current.value = read(clock, appointments)
    }

    private suspend fun read(clock: ClinicClock, source: AppointmentsSource): CareLoad<AppointmentsContent> {
        val appointments = source.appointments()
        val policy = source.policy()
        return allOf(appointments, policy) {
            val today = clock.today()
            AppointmentsContent(
                today = today,
                upcoming = appointments.ok.upcoming(today),
                past = appointments.ok.past(today),
                cancelled = appointments.ok.cancelled(),
                changeWindowHours = policy.ok.changeWindowHours,
            )
        }
    }
}
