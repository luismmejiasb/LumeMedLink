package com.luismejias.lumemedlink.features.patient

import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.BookingFailure
import com.luismejias.lumemedlink.core.care.CareFailure
import com.luismejias.lumemedlink.core.care.DirectoryScript
import com.luismejias.lumemedlink.core.care.ScriptedPatientCare
import com.luismejias.lumemedlink.features.patient.parts.CareLoad
import com.luismejias.lumemedlink.features.patient.search.BookingOutcome
import com.luismejias.lumemedlink.features.patient.search.SearchFilters
import com.luismejias.lumemedlink.features.patient.search.SearchRoute
import com.luismejias.lumemedlink.features.patient.search.SearchSort
import com.luismejias.lumemedlink.features.patient.search.SearchViewModel
import com.luismejias.lumemedlink.shared.ClockTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Synthetic only (§9): the design's sample doctors.
@OptIn(ExperimentalCoroutinesApi::class)
class BookingFlowTest {
    private val script = ScriptedPatientCare(latency = {})
    private val model = SearchViewModel(script, script)

    @BeforeTest
    fun mainDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun resetDispatcher() = Dispatchers.resetMain()

    private suspend fun choose(specialty: String, commune: String) {
        model.openSpecialty()
        val specialties = (model.specialties.state.value as CareLoad.Ready).content.all
        model.specialtyChosen(specialties.first { it.name == specialty })
        model.openCommune()
        val communes = (model.communes.state.value as CareLoad.Ready).content.all
        model.communeChosen(communes.first { it.name == commune })
    }

    private fun results() = (model.results.state.value as CareLoad.Ready).content

    @Test
    fun aSynonymFindsTheSpecialtyAndAccentsDoNotMatter() = runTest {
        model.openSpecialty()
        model.specialties.queryChanged("piel")
        val found = (model.specialties.state.value as CareLoad.Ready).content.matches.map { it.name }
        assertEquals(listOf("Dermatología", "Dermatología pediátrica"), found)
        model.specialties.queryChanged("TRAUMATOLOGIA")
        assertEquals(1, (model.specialties.state.value as CareLoad.Ready).content.matches.size)
    }

    @Test
    fun searchingNeedsBothChoicesThenListsTheDoctors() = runTest {
        model.search()
        assertEquals(SearchRoute.ROOT, model.flow.top, "half a form searches nothing")
        choose("Medicina general", "Providencia")
        assertTrue(model.criteria.value.complete)
        model.search()
        assertEquals(SearchRoute.RESULTS, model.flow.top)
        assertEquals(3, results().shown.size)
    }

    @Test
    fun filtersSortAndNarrowWithoutAskingAgain() = runTest {
        choose("Medicina general", "Providencia")
        model.search()
        model.results.setFilters(SearchFilters(sort = SearchSort.BEST_RATED))
        assertEquals("p-rendon", results().shown.first().doctor.id)
        model.results.setFilters(SearchFilters(sort = SearchSort.MOST_ATTENDED, fourStarsUp = true))
        assertEquals(2, results().filters.activeCount)
        model.openFilters()
        model.results.draftChanged(SearchFilters())
        model.filtersApplied()
        assertEquals(0, results().filters.activeCount)
        assertEquals(SearchRoute.RESULTS, model.flow.top)
    }

    @Test
    fun anEmptySearchOffersNearbyCommunes() = runTest {
        choose("Cardiología", "Vitacura")
        model.search()
        assertTrue(results().doctors.isEmpty())
        assertEquals(setOf("Las Condes", "Providencia"), results().nearby.map { it.commune.name }.toSet())
        model.nearbyChosen(results().nearby.first { it.commune.name == "Las Condes" }.commune)
        assertEquals(1, results().doctors.size)
    }

    @Test
    fun offlineIsAStateOfTheResultsNotAnEmptyList() = runTest {
        choose("Medicina general", "Ñuñoa")
        model.search()
        assertEquals(CareLoad.Failed(CareFailure.Offline), model.results.state.value)
        assertEquals(DirectoryScript.OFFLINE_COMMUNE, model.criteria.value.commune?.code, "what was searched stays")
    }

    @Test
    fun aBookingIsARequestThatShowsUpInMisCitas() = runTest {
        choose("Medicina general", "Providencia")
        model.search()
        val rendon = results().shown.first { it.doctor.id == "p-rendon" }
        model.schedule(rendon)
        val schedule = (model.schedule.state.value as CareLoad.Ready).content
        val slot = schedule.morning.first()
        model.schedule.slotChosen(slot)
        model.confirm(rendon, slot)
        model.booking.book()
        val sent = assertIs<BookingOutcome.Sent>(model.booking.state.value?.outcome)
        assertEquals(AppointmentStatus.REQUESTED, sent.appointment.status)
        val mine = (script.appointments() as com.luismejias.lumemedlink.core.care.CareResult.Ok).value
        assertTrue(mine.any { it.id == sent.appointment.id })
        model.finishBooking()
        assertEquals(SearchRoute.ROOT, model.flow.top)
    }

    @Test
    fun aRetryOfTheSameConfirmationNeverBooksTwice() = runTest {
        choose("Medicina general", "Providencia")
        model.search()
        val vera = results().shown.first { it.doctor.id == "p-vera" }
        model.schedule(vera)
        val slot = (model.schedule.state.value as CareLoad.Ready).content.let { it.morning + it.afternoon }.first()
        model.confirm(vera, slot)
        model.booking.book()
        val first = assertIs<BookingOutcome.Sent>(model.booking.state.value?.outcome).appointment
        model.booking.outcomeSeen()
        model.booking.book()
        val again = assertIs<BookingOutcome.Sent>(model.booking.state.value?.outcome).appointment
        assertEquals(first.id, again.id, "the client key makes the retry answer the same appointment")
    }

    @Test
    fun anHourTakenOnArrivalSendsThePatientBackToChooseAnother() = runTest {
        choose("Medicina general", "Providencia")
        model.search()
        val rendon = results().shown.first { it.doctor.id == "p-rendon" }
        model.schedule(rendon)
        val monday = script.today().plusDays(5)
        model.schedule.dayChosen(monday)
        val ten = (model.schedule.state.value as CareLoad.Ready).content.morning.firstOrNull {
            it.start ==
                ClockTime(10, 0)
        }
        assertNotNull(ten, "the script publishes Monday 10:00 for Dra. Rendón")
        model.confirm(rendon, ten)
        model.booking.book()
        val refused = assertIs<BookingOutcome.Refused>(model.booking.state.value?.outcome)
        assertEquals(BookingFailure.SlotTaken, refused.failure)
        model.chooseAnotherHour()
        assertEquals(SearchRoute.SCHEDULE, model.flow.top)
        assertEquals(monday, (model.schedule.state.value as CareLoad.Ready).content.day, "back on the same day")
    }

    @Test
    fun anHourThePatientAlreadyHoldsIsNotOfferedAndIsExplained() = runTest {
        choose("Cardiología", "Las Condes")
        model.search()
        model.schedule(results().doctors.single())
        val monday = script.today().plusDays(5)
        model.schedule.dayChosen(monday)
        val content = (model.schedule.state.value as CareLoad.Ready).content
        assertFalse(content.morning.any { it.start == ClockTime(9, 0) }, "the patient has Dr. Pinto at 09:00")
        assertEquals(listOf("p-pinto"), content.clashes.map { it.doctor.id })
    }
}
