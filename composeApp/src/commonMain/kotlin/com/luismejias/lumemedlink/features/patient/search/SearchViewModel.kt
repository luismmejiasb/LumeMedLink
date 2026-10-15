package com.luismejias.lumemedlink.features.patient.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.AppointmentsSource
import com.luismejias.lumemedlink.core.care.BookingGateway
import com.luismejias.lumemedlink.core.care.ClinicClock
import com.luismejias.lumemedlink.core.care.Commune
import com.luismejias.lumemedlink.core.care.DirectoryAvailability
import com.luismejias.lumemedlink.core.care.DirectoryDoctor
import com.luismejias.lumemedlink.core.care.DirectoryGateway
import com.luismejias.lumemedlink.core.care.DirectorySearch
import com.luismejias.lumemedlink.core.care.PatientCareGateway
import com.luismejias.lumemedlink.core.care.Slot
import com.luismejias.lumemedlink.core.care.Specialty
import com.luismejias.lumemedlink.core.care.standInPatientCare
import com.luismejias.lumemedlink.core.telemetry.AnalyticsEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What Buscar reads and writes, each capability on its own (interface segregation, task 0036). */
internal class SearchSources(
    val clock: ClinicClock,
    val appointments: AppointmentsSource,
    val directory: DirectorySearch,
    val availability: DirectoryAvailability,
    val booking: BookingGateway,
)

/** Every source of Buscar from what implements all of them: the script, for the stand-in and the tests. */
internal fun searchSourcesOf(care: PatientCareGateway, directory: DirectoryGateway): SearchSources =
    SearchSources(care, care, directory, directory, directory)

/**
 * Buscar's owner (ADR-0045): the tab's stack and every screen's model on it, from the search (p2) to the
 * booking (a2). Each step is a suspend function a test can call; the screens launch them.
 */
internal class SearchViewModel(sources: SearchSources, logEvent: (AnalyticsEvent) -> Unit = {}) : ViewModel() {
    private val directory = sources.directory
    val flow = SearchFlow()

    private val chosen = MutableStateFlow(SearchCriteria())
    val criteria: StateFlow<SearchCriteria> = chosen.asStateFlow()

    val specialties = CatalogPicker(
        read = { directory.specialties() },
        standInRead = { standInPatientCare().specialties() },
    ) { listOf(it.name) + it.synonyms }
    val communes = CatalogPicker(
        read = { directory.communes() },
        standInRead = { standInPatientCare().communes() },
    ) { listOf(it.name) }
    val results = ResultsModel(directory, sources.clock::today) {
        standInPatientCare().let { stand -> ResultsModel(stand, stand::today) }
    }
    val schedule = ScheduleModel(sources.availability, sources.appointments::appointments, sources.clock::today) {
        standInPatientCare().let { stand -> ScheduleModel(stand, stand::appointments, stand::today) }
    }
    val booking = BookingModel(sources.booking, logEvent)

    init {
        // Preloaded (the author's call, 2026-10-15): the catalog does not depend on anything chosen, so the picker
        // opens on it rather than on a wait.
        launch { communes.load() }
    }

    /** Runs a step of the flow in this ViewModel's scope — a screen never owns the work it starts. */
    fun launch(step: suspend SearchViewModel.() -> Unit) {
        viewModelScope.launch { step() }
    }

    // ── The form (p2) ──────────────────────────────────────────────────────────────────────────

    suspend fun openSpecialty() {
        flow.push(SearchRoute.SPECIALTY)
        specialties.load()
    }

    /** The catalog was read when Buscar opened; only a failed or missing read is read again here. */
    suspend fun openCommune() {
        flow.push(SearchRoute.COMMUNE)
        communes.loadUnlessReady()
    }

    /** The two choices are independent: the commune list is the whole catalog, whatever the specialty. */
    fun specialtyChosen(specialty: Specialty) {
        chosen.value = chosen.value.copy(specialty = specialty)
        flow.back()
    }

    fun communeChosen(commune: Commune) {
        chosen.value = chosen.value.copy(commune = commune)
        flow.back()
    }

    suspend fun search() {
        if (!chosen.value.complete) return
        results.clear()
        if (flow.top != SearchRoute.RESULTS) flow.push(SearchRoute.RESULTS)
        results.load(chosen.value)
    }

    /** e5: «Las Condes · 3 médicos» searches the same specialty there. */
    suspend fun nearbyChosen(commune: Commune) {
        chosen.value = chosen.value.copy(commune = commune)
        search()
    }

    // ── Filters (p4) ───────────────────────────────────────────────────────────────────────────

    fun openFilters() {
        results.openFilters()
        flow.push(SearchRoute.FILTERS)
    }

    /** p4's «Disponibilidad» opens its own list, as Especialidad does (spec p4). */
    fun openAvailability() = flow.push(SearchRoute.AVAILABILITY)

    /** A choice in that list edits p4's draft and returns to it. */
    fun availabilityChosen(window: AvailabilityWindow) {
        results.draftChanged(results.draft.value.copy(window = window))
        flow.back()
    }

    fun filtersApplied() {
        results.applyDraft()
        flow.back()
    }

    // ── Booking (a1, a2) ───────────────────────────────────────────────────────────────────────

    suspend fun schedule(doctor: DirectoryDoctor) {
        flow.push(SearchRoute.SCHEDULE)
        schedule.load(doctor)
    }

    fun confirm(doctor: DirectoryDoctor, slot: Slot) {
        booking.start(doctor, slot)
        flow.push(SearchRoute.CONFIRM)
    }

    /** The hour was taken: back to a1 with fresh hours, on the same day. */
    suspend fun chooseAnotherHour() {
        val state = booking.state.value ?: return
        booking.outcomeSeen()
        flow.backTo(SearchRoute.SCHEDULE)
        schedule.load(state.doctor, keepDay = state.slot.date)
    }

    /** a3's «Listo» or «Ver mis citas»: the flow is over; the tab starts again from its form. */
    fun finishBooking() {
        booking.clear()
        flow.reset()
    }
}
