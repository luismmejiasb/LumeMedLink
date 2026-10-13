package com.luismejias.lumemedlink.features.patient.search

import com.luismejias.lumemedlink.core.care.BookingFailure
import com.luismejias.lumemedlink.core.care.BookingResult
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.Commune
import com.luismejias.lumemedlink.core.care.DirectoryDoctor
import com.luismejias.lumemedlink.core.care.DirectoryGateway
import com.luismejias.lumemedlink.core.care.NearbyCommune
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.Slot
import com.luismejias.lumemedlink.core.care.Specialty
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.core.screen.allOf
import com.luismejias.lumemedlink.core.screen.folded
import com.luismejias.lumemedlink.core.screen.markLoading
import com.luismejias.lumemedlink.core.screen.ok
import com.luismejias.lumemedlink.core.screen.toLoad
import com.luismejias.lumemedlink.core.telemetry.AnalyticsEvent
import com.luismejias.lumemedlink.features.patient.parts.upcoming
import com.luismejias.lumemedlink.shared.CalendarDate
import com.luismejias.lumemedlink.shared.ClockTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Buscar's form (p2): both choices are required before "Buscar médicos" turns on. */
internal data class SearchCriteria(val specialty: Specialty? = null, val commune: Commune? = null) {
    val complete: Boolean get() = specialty != null && commune != null
}

// ── The two catalogs (e0–e3) ────────────────────────────────────────────────────────────────────

/** A catalog and what is typed to narrow it. Matching ignores case and accents, and reads synonyms. */
internal data class PickerContent<T>(
    val all: List<T>,
    val query: String = "",
    private val terms: (T) -> List<String>,
) {
    val matches: List<T>
        get() {
            val wanted = query.folded()
            return if (wanted.isEmpty()) all else all.filter { item -> terms(item).any { wanted in it.folded() } }
        }

    val searching: Boolean get() = query.isNotBlank()
}

internal class CatalogPicker<T>(
    private val read: suspend () -> CareResult<List<T>>,
    private val terms: (T) -> List<String>,
) {
    private val current = MutableStateFlow<CareLoad<PickerContent<T>>>(CareLoad.Loading)
    val state: StateFlow<CareLoad<PickerContent<T>>> = current.asStateFlow()

    suspend fun load() {
        current.value = CareLoad.Loading
        current.value = read().toLoad { PickerContent(it, terms = terms) }
    }

    fun queryChanged(query: String) {
        val ready = current.value as? CareLoad.Ready ?: return
        current.value = CareLoad.Ready(ready.content.copy(query = query))
    }
}

// ── Results and filters (p3, p4, e5, e6) ────────────────────────────────────────────────────────

/**
 * The order of p4. «Más cerca» is NOT here: it needs the patient's location, and a coordinate is no excuse for a
 * «cerca de ti» (constitution §1.0, ADR-0027).
 */
internal enum class SearchSort { SOONEST, BEST_RATED, MOST_ATTENDED }

internal enum class AvailabilityWindow { ANY, THIS_WEEK }

/** p4's choices. The default is the soonest hour, any day, any rating: nothing to count. */
internal data class SearchFilters(
    val sort: SearchSort = SearchSort.SOONEST,
    val window: AvailabilityWindow = AvailabilityWindow.ANY,
    val fourStarsUp: Boolean = false,
) {
    val activeCount: Int
        get() = listOf(sort != SearchSort.SOONEST, window != AvailabilityWindow.ANY, fourStarsUp).count { it }

    fun applyTo(doctors: List<DirectoryDoctor>, today: CalendarDate): List<DirectoryDoctor> {
        val weekEnd = today.plusDays(DAYS_IN_WEEK - today.isoWeekday)
        val kept = doctors.filter { entry ->
            val inWindow = window == AvailabilityWindow.ANY || (entry.nextSlot?.date?.let { it <= weekEnd } ?: false)
            val rated = !fourStarsUp || (entry.rating ?: 0.0) >= MIN_STARS
            inWindow && rated
        }
        return when (sort) {
            SearchSort.SOONEST -> kept.sortedWith(
                compareBy<DirectoryDoctor, CalendarDate?>(nullsLast()) { it.nextSlot?.date }
                    .thenBy(nullsLast()) { it.nextSlot?.start },
            )
            SearchSort.BEST_RATED -> kept.sortedByDescending { it.rating ?: 0.0 }
            SearchSort.MOST_ATTENDED -> kept.sortedByDescending { it.attentions }
        }
    }

    private companion object {
        const val DAYS_IN_WEEK = 7
        const val MIN_STARS = 4.0
    }
}

internal data class ResultsContent(
    val today: CalendarDate,
    val doctors: List<DirectoryDoctor>,
    /** e5: communes nearby that do have the specialty, read only when the search came back empty. */
    val nearby: List<NearbyCommune>,
    val filters: SearchFilters = SearchFilters(),
) {
    val shown: List<DirectoryDoctor> get() = filters.applyTo(doctors, today)

    override fun toString(): String = "ResultsContent(doctors=${doctors.size})"
}

internal class ResultsModel(private val directory: DirectoryGateway, private val today: () -> CalendarDate) {
    private val current = MutableStateFlow<CareLoad<ResultsContent>>(CareLoad.Loading)
    val state: StateFlow<CareLoad<ResultsContent>> = current.asStateFlow()

    /** p4 edits a draft; «Ver N médicos» applies it, «Limpiar» resets it. */
    private val drafting = MutableStateFlow(SearchFilters())
    val draft: StateFlow<SearchFilters> = drafting.asStateFlow()

    suspend fun load(criteria: SearchCriteria) {
        val specialty = criteria.specialty ?: return
        val commune = criteria.commune ?: return
        val filters = (current.value as? CareLoad.Ready)?.content?.filters ?: SearchFilters()
        current.markLoading()
        val found = directory.search(specialty.code, commune.code)
        val nearby = if ((found as? CareResult.Ok)?.value?.isEmpty() == true) {
            directory.nearbyCommunes(specialty.code, commune.code)
        } else {
            CareResult.Ok(emptyList())
        }
        current.value = allOf(found, nearby) { ResultsContent(today(), found.ok, nearby.ok, filters) }
    }

    /** A new search starts from a clean slate: what the last one showed is not this one's. */
    fun clear() {
        current.value = CareLoad.Loading
        drafting.value = SearchFilters()
    }

    fun openFilters() {
        drafting.value = (current.value as? CareLoad.Ready)?.content?.filters ?: SearchFilters()
    }

    fun draftChanged(filters: SearchFilters) {
        drafting.value = filters
    }

    /** How many «Ver N médicos» would show. */
    fun draftCount(): Int =
        (current.value as? CareLoad.Ready)?.content?.let { drafting.value.applyTo(it.doctors, it.today).size } ?: 0

    fun applyDraft() = setFilters(drafting.value)

    fun setFilters(filters: SearchFilters) {
        val ready = current.value as? CareLoad.Ready ?: return
        current.value = CareLoad.Ready(ready.content.copy(filters = filters))
        drafting.value = filters
    }
}

// ── Day and hour (a1) ───────────────────────────────────────────────────────────────────────────

private val NOON = ClockTime(12, 0)

/** A month of the calendar, as a1's month and year menus choose it. */
internal data class CalendarMonth(val year: Int, val month: Int) : Comparable<CalendarMonth> {
    val first: CalendarDate get() = CalendarDate(year, month, 1)

    val last: CalendarDate get() = CalendarDate(year, month, CalendarDate.daysIn(year, month))

    override fun compareTo(other: CalendarMonth): Int = compareValuesBy(this, other, { it.year }, { it.month })
}

internal val CalendarDate.calendarMonth: CalendarMonth get() = CalendarMonth(year, month)

/** One hour of a1's grid: a free one to choose, or one the patient already holds («Tu cita»), which is refused. */
internal sealed interface HourTile {
    val start: ClockTime

    data class Free(val slot: Slot) : HourTile {
        override val start: ClockTime get() = slot.start
    }

    data class Mine(val appointment: PatientAppointment) : HourTile {
        override val start: ClockTime get() = appointment.start
    }
}

internal data class ScheduleContent(
    val doctor: DirectoryDoctor,
    /** The free hours, minus any that falls on an appointment the patient already has. */
    val slots: List<Slot>,
    /** The patient's own upcoming appointments: «A las 09:00 ya tienes cita con el Dr. Pinto». */
    val own: List<PatientAppointment>,
    val day: CalendarDate?,
    val slot: Slot?,
    /** The booking window: tomorrow to the horizon. */
    val firstDay: CalendarDate,
    val lastDay: CalendarDate,
    val month: CalendarMonth,
) {
    /** The days that have a free hour. */
    val days: List<CalendarDate> get() = slots.map { it.date }.distinct()

    /** The months with open agenda — the only ones the month menu offers (spec a1). */
    val months: List<CalendarMonth> get() = days.map { it.calendarMonth }.distinct()

    val years: List<Int> get() = months.map { it.year }.distinct()

    /** Every day of the chosen month inside the window; one without hours is drawn and refused. */
    val strip: List<CalendarDate>
        get() {
            val from = maxOf(firstDay, month.first)
            val to = minOf(lastDay, month.last)
            return if (from > to) emptyList() else (0..from.daysUntil(to)).map { from.plusDays(it) }
        }

    val morning: List<HourTile> get() = tiles().filter { it.start < NOON }

    val afternoon: List<HourTile> get() = tiles().filter { it.start >= NOON }

    /** The patient's appointments on the day shown — why an hour they might expect is missing. */
    val clashes: List<PatientAppointment> get() = own.filter { it.date == day }

    val morningClashes: List<PatientAppointment> get() = clashes.filter { it.start < NOON }

    val afternoonClashes: List<PatientAppointment> get() = clashes.filter { it.start >= NOON }

    private fun tiles(): List<HourTile> =
        (slots.filter { it.date == day }.map { HourTile.Free(it) } + clashes.map { HourTile.Mine(it) })
            .sortedBy { it.start }

    override fun toString(): String = "ScheduleContent(slots=${slots.size}, chosen=${slot != null})"
}

internal class ScheduleModel(
    private val directory: DirectoryGateway,
    private val ownAppointments: suspend () -> CareResult<List<PatientAppointment>>,
    private val today: () -> CalendarDate,
) {
    private val current = MutableStateFlow<CareLoad<ScheduleContent>>(CareLoad.Loading)
    val state: StateFlow<CareLoad<ScheduleContent>> = current.asStateFlow()

    /** Reads a doctor's hours from tomorrow to the booking horizon; [keepDay] stays on a day after a retry. */
    suspend fun load(doctor: DirectoryDoctor, keepDay: CalendarDate? = null) {
        current.value = CareLoad.Loading
        val from = today().plusDays(1)
        val to = from.plusDays(HORIZON_DAYS)
        val free = directory.availability(doctor.doctor.id, doctor.locationId, from, to)
        val own = ownAppointments()
        current.value = allOf(free, own) {
            val mine = own.ok.upcoming(today())
            val slots = free.ok.filterNot { slot -> mine.any { it.date == slot.date && it.start == slot.start } }
            val days = slots.map { it.date }.distinct()
            val day =
                keepDay?.takeIf { it in days } ?: doctor.nextSlot?.date?.takeIf { it in days } ?: days.firstOrNull()
            ScheduleContent(doctor, slots, mine, day, null, from, to, (day ?: from).calendarMonth)
        }
    }

    fun dayChosen(day: CalendarDate) = update {
        if (day in days) copy(day = day, slot = null, month = day.calendarMonth) else this
    }

    /** A new month opens on its first day with hours. */
    fun monthChosen(month: CalendarMonth) = update {
        copy(month = month, day = days.firstOrNull { it.calendarMonth == month }, slot = null)
    }

    /** A new year opens on its first month with agenda. */
    fun yearChosen(year: Int) = update {
        months.firstOrNull { it.year == year }?.let { month ->
            copy(month = month, day = days.firstOrNull { it.calendarMonth == month }, slot = null)
        } ?: this
    }

    fun slotChosen(slot: Slot?) = update { copy(slot = slot) }

    private fun update(change: ScheduleContent.() -> ScheduleContent) {
        val ready = current.value as? CareLoad.Ready ?: return
        current.value = CareLoad.Ready(ready.content.change())
    }

    private companion object {
        const val HORIZON_DAYS = 30
    }
}

// ── Confirm and send (a2, a3) ───────────────────────────────────────────────────────────────────

/** What the confirmation answered. */
internal sealed interface BookingOutcome {
    data class Sent(val appointment: PatientAppointment) : BookingOutcome

    data class Refused(val failure: BookingFailure) : BookingOutcome
}

internal data class BookingState(val doctor: DirectoryDoctor, val slot: Slot, val outcome: BookingOutcome? = null) {
    override fun toString(): String = "BookingState(outcome=${outcome?.let { it::class.simpleName }})"
}

@OptIn(ExperimentalUuidApi::class)
internal class BookingModel(
    private val directory: DirectoryGateway,
    private val logEvent: (AnalyticsEvent) -> Unit = {},
) {
    private val current = MutableStateFlow<BookingState?>(null)
    val state: StateFlow<BookingState?> = current.asStateFlow()

    /**
     * The key of THIS booking (the contract's `clientId`): made when a slot is brought to confirm, and reused by
     * every retry of it, so a dropped connection after the write never makes a second appointment.
     */
    private var clientId: String = Uuid.random().toString()

    fun start(doctor: DirectoryDoctor, slot: Slot) {
        if (current.value?.let { it.doctor.doctor.id == doctor.doctor.id && it.slot == slot } != true) {
            clientId = Uuid.random().toString()
        }
        current.value = BookingState(doctor, slot)
    }

    suspend fun book() {
        val booking = current.value ?: return
        val result = directory.book(booking.doctor.doctor.id, booking.doctor.locationId, booking.slot, clientId)
        current.value = booking.copy(
            outcome = when (result) {
                is BookingResult.Booked -> BookingOutcome.Sent(result.appointment).also {
                    logEvent(AnalyticsEvent.APPOINTMENT_BOOKED)
                }
                is BookingResult.Failed -> BookingOutcome.Refused(result.failure)
            },
        )
    }

    /** The alert was answered: the screen is the confirmation again. */
    fun outcomeSeen() {
        current.value = current.value?.copy(outcome = null)
    }

    fun clear() {
        current.value = null
    }
}
