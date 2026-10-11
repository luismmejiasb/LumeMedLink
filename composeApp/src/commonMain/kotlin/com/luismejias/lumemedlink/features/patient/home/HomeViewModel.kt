package com.luismejias.lumemedlink.features.patient.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.PatientCareGateway
import com.luismejias.lumemedlink.core.care.RatingPrompt
import com.luismejias.lumemedlink.features.patient.parts.CareLoad
import com.luismejias.lumemedlink.features.patient.parts.allOf
import com.luismejias.lumemedlink.features.patient.parts.awaitingConfirmation
import com.luismejias.lumemedlink.features.patient.parts.markLoading
import com.luismejias.lumemedlink.features.patient.parts.ok
import com.luismejias.lumemedlink.features.patient.parts.upcoming
import com.luismejias.lumemedlink.shared.CalendarDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What Inicio draws (p1, e7). */
internal data class HomeContent(
    val givenNames: String,
    val initials: String,
    val today: CalendarDate,
    /** The hero card: the soonest appointment the practice has confirmed. Null draws e7. */
    val next: PatientAppointment?,
    /** "Esperando confirmación": requests the practice has not answered. */
    val waiting: List<PatientAppointment>,
    /** "¿Cómo fue tu atención?": the most recent attended appointment not yet rated. */
    val rating: RatingPrompt?,
    val unreadNotices: Boolean,
) {
    override fun toString(): String = "HomeContent(redacted)"
}

/** Inicio's owner (ADR-0045: a ViewModel per tab root). */
internal class HomeViewModel(private val care: PatientCareGateway) : ViewModel() {
    private val current = MutableStateFlow<CareLoad<HomeContent>>(CareLoad.Loading)
    val state: StateFlow<CareLoad<HomeContent>> = current.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        current.markLoading()
        val profile = care.profile()
        val appointments = care.appointments()
        val ratings = care.ratingPrompts()
        val notices = care.hasUnreadNotices()
        current.value = allOf(profile, appointments, ratings, notices) {
            val today = care.today()
            val ahead = appointments.ok.upcoming(today)
            HomeContent(
                givenNames = profile.ok.givenNames,
                initials = profile.ok.initials,
                today = today,
                next = ahead.firstOrNull { it.status != AppointmentStatus.REQUESTED },
                waiting = ahead.awaitingConfirmation(today),
                rating = ratings.ok.maxByOrNull { it.date },
                unreadNotices = notices.ok,
            )
        }
    }
}
