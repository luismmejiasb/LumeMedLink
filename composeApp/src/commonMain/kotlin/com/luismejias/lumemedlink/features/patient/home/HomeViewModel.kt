package com.luismejias.lumemedlink.features.patient.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.AppointmentsSource
import com.luismejias.lumemedlink.core.care.CappedSecondaryRead
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.ClinicClock
import com.luismejias.lumemedlink.core.care.NoticesSource
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.PatientCareGateway
import com.luismejias.lumemedlink.core.care.PatientProfile
import com.luismejias.lumemedlink.core.care.PatientProfileSource
import com.luismejias.lumemedlink.core.care.RatingPrompt
import com.luismejias.lumemedlink.core.care.RatingsGateway
import com.luismejias.lumemedlink.core.care.secondary
import com.luismejias.lumemedlink.core.care.standInPatientCare
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.core.screen.PullRefresh
import com.luismejias.lumemedlink.core.screen.StandIn
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
    /** Null when the profile could not be read: «Hola» alone, and the profile disc without initials. */
    val givenNames: String?,
    val initials: String?,
    val today: CalendarDate,
    /**
     * Whether the appointments could be read. When not, Inicio draws neither the hero card nor e7: «no tienes citas»
     * would be a claim it cannot make.
     */
    val appointmentsRead: Boolean,
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
    /**
     * Inicio has no operation of its own (ADR-0067): every piece of it belongs to another screen, so nothing it reads
     * can fail it. The type says so — null is waiting, and there is no failure to hold.
     */
    private val current = MutableStateFlow<HomeContent?>(null)
    val state: StateFlow<HomeContent?> = current.asStateFlow()

    /** The screen's own shape while it waits (ADR-0055). */
    private val standInContent = StandIn<HomeContent>()
    val standIn: StateFlow<HomeContent?> = standInContent.content

    /** The bell's dot. The notices screen keeps its own read, with its own failure and retry. */
    private val bellDot = CappedSecondaryRead(fallback = 0) { sources.notices.unreadNoticeCount() }

    /** «¿Cómo fue tu atención?»: without it Inicio is still Inicio, so a failure only leaves the card out. */
    private val ratingPrompts = CappedSecondaryRead(fallback = emptyList()) { sources.ratings.ratingPrompts() }

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
            CareLoad.Ready(
                read(
                    stand,
                    // The waiting shape draws no count: a red badge over a skeleton reads as news that is not there.
                    bell = { 0 },
                    ratings = { (stand.ratings.ratingPrompts() as? CareResult.Ok)?.value.orEmpty() },
                ),
            )
        }
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
    ): HomeContent = coroutineScope {
        // Re-read at every load and pull: Inicio's main pieces, read only when the person opens it or pulls.
        val profileRead = async { secondary<PatientProfile?>(null) { sources.profile.profile() } }
        val appointmentsRead = async {
            secondary<List<PatientAppointment>?>(null) { sources.appointments.appointments() }
        }
        val prompts = async { ratings() }
        val unread = async { bell() }
        val profile = profileRead.await()
        val today = sources.clock.today()
        val ahead = appointmentsRead.await()?.upcoming(today)
        HomeContent(
            givenNames = profile?.givenNames,
            initials = profile?.initials,
            today = today,
            appointmentsRead = ahead != null,
            next = ahead?.firstOrNull { it.status != AppointmentStatus.REQUESTED },
            waiting = ahead?.awaitingConfirmation(today).orEmpty(),
            rating = prompts.await().maxByOrNull { it.date },
            unreadNotices = unread.await(),
        )
    }
}
