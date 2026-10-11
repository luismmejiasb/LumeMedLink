package com.luismejias.lumemedlink.features.patient.appointments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.PatientCareGateway
import com.luismejias.lumemedlink.features.patient.parts.CareLoad
import com.luismejias.lumemedlink.features.patient.parts.allOf
import com.luismejias.lumemedlink.features.patient.parts.awaitingConfirmation
import com.luismejias.lumemedlink.features.patient.parts.markLoading
import com.luismejias.lumemedlink.features.patient.parts.ok
import com.luismejias.lumemedlink.features.patient.parts.past
import com.luismejias.lumemedlink.features.patient.parts.upcoming
import com.luismejias.lumemedlink.shared.CalendarDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class AppointmentsSegment { UPCOMING, PAST }

/** What Mis citas draws (p5, e8). */
internal data class AppointmentsContent(
    val today: CalendarDate,
    val upcoming: List<PatientAppointment>,
    val past: List<PatientAppointment>,
    /** "Puedes cambiar o cancelar hasta 24 h antes": the practice's window. */
    val changeWindowHours: Int,
) {
    /** The tab's badge: requests still waiting for the practice. */
    val awaiting: Int get() = upcoming.awaitingConfirmation(today).size

    override fun toString(): String = "AppointmentsContent(upcoming=${upcoming.size}, past=${past.size})"
}

/** Mis citas' owner (ADR-0045). Its count also feeds the tab bar's badge. */
internal class AppointmentsViewModel(private val care: PatientCareGateway) : ViewModel() {
    private val current = MutableStateFlow<CareLoad<AppointmentsContent>>(CareLoad.Loading)
    val state: StateFlow<CareLoad<AppointmentsContent>> = current.asStateFlow()

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
        current.markLoading()
        val appointments = care.appointments()
        val policy = care.policy()
        current.value = allOf(appointments, policy) {
            val today = care.today()
            AppointmentsContent(
                today = today,
                upcoming = appointments.ok.upcoming(today),
                past = appointments.ok.past(today),
                changeWindowHours = policy.ok.changeWindowHours,
            )
        }
    }
}
