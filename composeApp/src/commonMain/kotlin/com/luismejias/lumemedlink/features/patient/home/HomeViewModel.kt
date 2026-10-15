package com.luismejias.lumemedlink.features.patient.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.AppointmentsSource
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.ClinicClock
import com.luismejias.lumemedlink.core.care.NoticesSource
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.PatientCareGateway
import com.luismejias.lumemedlink.core.care.PatientProfileSource
import com.luismejias.lumemedlink.core.care.RatingPrompt
import com.luismejias.lumemedlink.core.care.RatingsGateway
import com.luismejias.lumemedlink.core.care.standInPatientCare
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.core.screen.PullRefresh
import com.luismejias.lumemedlink.core.screen.StandIn
import com.luismejias.lumemedlink.core.screen.allOf
import com.luismejias.lumemedlink.core.screen.markLoading
import com.luismejias.lumemedlink.core.screen.ok
import com.luismejias.lumemedlink.features.patient.parts.awaitingConfirmation
import com.luismejias.lumemedlink.features.patient.parts.upcoming
import com.luismejias.lumemedlink.shared.CalendarDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    /** How many notices wait behind the bell; 0 when none, or when the count could not be read. */
    val unreadNotices: Int,
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

    /** The bell's dot. The notices screen keeps its own read, with its own failure and retry. */
    private val bellDot = SecondaryRead(fallback = 0) { sources.notices.unreadNoticeCount() }

    /** «¿Cómo fue tu atención?»: without it Inicio is still Inicio, so a failure only leaves the card out. */
    private val ratingPrompts = SecondaryRead(fallback = emptyList()) { sources.ratings.ratingPrompts() }

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

    suspend fun load() {
        standInContent.fill {
            val stand = standInPatientCare().homeSources()
            read(
                stand,
                // The waiting shape draws no count: a red badge over a skeleton reads as news that is not there.
                bell = { 0 },
                ratings = { (stand.ratings.ratingPrompts() as? CareResult.Ok)?.value.orEmpty() },
            )
        }
        current.markLoading()
        current.value = read(sources, bell = bellDot::get, ratings = ratingPrompts::get)
    }

    /**
     * The reads run together: one after another, a backend that refuses made a pull to refresh spin for the sum
     * of four bounded retries before the failure showed (seen 2026-10-15).
     */
    private suspend fun read(
        sources: HomeSources,
        bell: suspend () -> Int,
        ratings: suspend () -> List<RatingPrompt>,
    ): CareLoad<HomeContent> = coroutineScope {
        val profileRead = async { sources.profile.profile() }
        val appointmentsRead = async { sources.appointments.appointments() }
        val prompts = async { ratings() }
        val unread = async { bell() }
        val profile = profileRead.await()
        val appointments = appointmentsRead.await()
        allOf(profile, appointments) {
            val today = sources.clock.today()
            val ahead = appointments.ok.upcoming(today)
            HomeContent(
                givenNames = profile.ok.givenNames,
                initials = profile.ok.initials,
                today = today,
                next = ahead.firstOrNull { it.status != AppointmentStatus.REQUESTED },
                waiting = ahead.awaitingConfirmation(today),
                rating = prompts.await().maxByOrNull { it.date },
                unreadNotices = unread.await(),
            )
        }
    }
}

/**
 * A read Inicio can do without (the author, 2026-10-15): the bell's dot, the rating card. A failure gives [fallback]
 * instead of taking Inicio to its error state, and after [MAX_MISSES] failures in a row Inicio stops asking for it —
 * a background read is never retried without end. A success starts the count again.
 */
private class SecondaryRead<T>(private val fallback: T, private val read: suspend () -> CareResult<T>) {
    private var misses = 0

    suspend fun get(): T {
        if (misses >= MAX_MISSES) return fallback
        return when (val answer = read()) {
            is CareResult.Ok -> answer.value.also { misses = 0 }
            is CareResult.Failed -> fallback.also { misses++ }
        }
    }
}

private const val MAX_MISSES = 3
