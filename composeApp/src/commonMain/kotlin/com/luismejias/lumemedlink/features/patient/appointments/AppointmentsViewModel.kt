package com.luismejias.lumemedlink.features.patient.appointments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.AppointmentsSource
import com.luismejias.lumemedlink.core.care.CarePolicy
import com.luismejias.lumemedlink.core.care.ClinicClock
import com.luismejias.lumemedlink.core.care.OverbookFailure
import com.luismejias.lumemedlink.core.care.OverbookRequest
import com.luismejias.lumemedlink.core.care.OverbookRequests
import com.luismejias.lumemedlink.core.care.OverbookResult
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.secondary
import com.luismejias.lumemedlink.core.care.standInPatientCare
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.core.screen.PullRefresh
import com.luismejias.lumemedlink.core.screen.StandIn
import com.luismejias.lumemedlink.core.screen.markLoading
import com.luismejias.lumemedlink.core.screen.toLoad
import com.luismejias.lumemedlink.features.patient.parts.awaitingConfirmation
import com.luismejias.lumemedlink.features.patient.parts.cancelled
import com.luismejias.lumemedlink.features.patient.parts.past
import com.luismejias.lumemedlink.features.patient.parts.upcoming
import com.luismejias.lumemedlink.shared.CalendarDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    /**
     * "Puedes cambiar o cancelar hasta 24 h antes": the practice's window. Null when it could not be read: a secondary
     * read (ADR-0067), so the notice is left out and the appointments still show.
     */
    val changeWindowHours: Int?,
    /**
     * «Solicitudes de sobrecupo» (ADR-0069): the ones still waiting, and the ones asked in the last
     * [RECENT_OVERBOOK_DAYS]. A secondary read (ADR-0067): unreadable, the section is left out.
     */
    val overbooks: List<OverbookRequest> = emptyList(),
) {
    /** The tab's badge: requests still waiting for the practice. */
    val awaiting: Int get() = upcoming.awaitingConfirmation(today).size

    override fun toString(): String =
        "AppointmentsContent(upcoming=${upcoming.size}, past=${past.size}, cancelled=${cancelled.size})"
}

/** Mis citas' owner (ADR-0045). Its count also feeds the tab bar's badge. */
internal class AppointmentsViewModel(
    private val clock: ClinicClock,
    private val appointments: AppointmentsSource,
    private val overbooks: OverbookRequests,
) : ViewModel() {
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

    /** The tab's pull to refresh: the same read, with the content kept on screen while it runs. */
    val pull = PullRefresh()

    fun pullToRefresh() {
        viewModelScope.launch { pull.run { load() } }
    }

    fun segmentChosen(segment: AppointmentsSegment) {
        shown.value = segment
    }

    /** Why the last withdrawal did not go through; null once another one starts. */
    private val withdrawal = MutableStateFlow<OverbookFailure?>(null)
    val withdrawalFailure: StateFlow<OverbookFailure?> = withdrawal.asStateFlow()

    /** Takes back a waiting request. One the practice answered first is simply read again, with its answer. */
    fun withdrawOverbook(request: OverbookRequest) {
        viewModelScope.launch {
            withdrawal.value = null
            when (val result = overbooks.withdrawOverbookRequest(request)) {
                is OverbookResult.Done -> load()
                is OverbookResult.Failed ->
                    if (result.failure == OverbookFailure.NoLongerWaiting) load() else withdrawal.value = result.failure
            }
        }
    }

    suspend fun load() {
        standInContent.fill { standInPatientCare().let { read(it, it, it) } }
        current.markLoading()
        current.value = read(clock, appointments, overbooks)
    }

    /** Both reads run together, so a refusing backend costs one bounded retry, not two in a row. */
    private suspend fun read(
        clock: ClinicClock,
        source: AppointmentsSource,
        requests: OverbookRequests,
    ): CareLoad<AppointmentsContent> = coroutineScope {
        // The appointments are this screen's operation; the window is a note on them (ADR-0067).
        val appointmentsRead = async { source.appointments() }
        val window = async { secondary<CarePolicy?>(null) { source.policy() }?.changeWindowHours }
        val asked = async { secondary(emptyList()) { requests.overbookRequests() } }
        val appointments = appointmentsRead.await()
        val windowHours = window.await()
        val overbookRequests = asked.await()
        appointments.toLoad { all ->
            val today = clock.today()
            AppointmentsContent(
                today = today,
                upcoming = all.upcoming(today),
                past = all.past(today),
                cancelled = all.cancelled(),
                changeWindowHours = windowHours,
                overbooks = overbookRequests.filter {
                    it.waiting || it.askedOn.daysUntil(today) <= RECENT_OVERBOOK_DAYS
                },
            )
        }
    }
}

/** How long an answered overbook request stays in «Citas»: long enough to read its answer. */
internal const val RECENT_OVERBOOK_DAYS = 30
