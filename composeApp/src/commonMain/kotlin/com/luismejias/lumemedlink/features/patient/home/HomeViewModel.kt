package com.luismejias.lumemedlink.features.patient.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.AppointmentsSource
import com.luismejias.lumemedlink.core.care.ClinicClock
import com.luismejias.lumemedlink.core.care.NoticesSource
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.PatientCareGateway
import com.luismejias.lumemedlink.core.care.PatientProfileSource
import com.luismejias.lumemedlink.core.care.RatingPrompt
import com.luismejias.lumemedlink.core.care.RatingsGateway
import com.luismejias.lumemedlink.core.care.standInPatientCare
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.core.screen.StandIn
import com.luismejias.lumemedlink.core.screen.allOf
import com.luismejias.lumemedlink.core.screen.markLoading
import com.luismejias.lumemedlink.core.screen.ok
import com.luismejias.lumemedlink.features.patient.parts.awaitingConfirmation
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

/** What Inicio reads, each capability on its own (interface segregation, task 0036). */
internal class HomeSources(
    val clock: ClinicClock,
    val profile: PatientProfileSource,
    val appointments: AppointmentsSource,
    val ratings: RatingsGateway,
    val notices: NoticesSource,
)

/** Every source of Inicio from one implementation of all of them: the script, for the stand-in and the tests. */
internal fun PatientCareGateway.homeSources(): HomeSources = HomeSources(this, this, this, this, this)

/** Inicio's owner (ADR-0045: a ViewModel per tab root). */
internal class HomeViewModel(private val sources: HomeSources) : ViewModel() {
    private val current = MutableStateFlow<CareLoad<HomeContent>>(CareLoad.Loading)
    val state: StateFlow<CareLoad<HomeContent>> = current.asStateFlow()

    /** The screen's own shape while it waits (ADR-0055). */
    private val standInContent = StandIn<HomeContent>()
    val standIn: StateFlow<HomeContent?> = standInContent.content

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        standInContent.fill { read(standInPatientCare().homeSources()) }
        current.markLoading()
        current.value = read(sources)
    }

    private suspend fun read(sources: HomeSources): CareLoad<HomeContent> {
        val profile = sources.profile.profile()
        val appointments = sources.appointments.appointments()
        val ratings = sources.ratings.ratingPrompts()
        val notices = sources.notices.hasUnreadNotices()
        return allOf(profile, appointments, ratings, notices) {
            val today = sources.clock.today()
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
