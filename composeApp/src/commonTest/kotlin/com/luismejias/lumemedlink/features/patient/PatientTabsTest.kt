package com.luismejias.lumemedlink.features.patient

import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.CareDoctor
import com.luismejias.lumemedlink.core.care.CareFailure
import com.luismejias.lumemedlink.core.care.CarePlace
import com.luismejias.lumemedlink.core.care.CareWorld
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.ScriptedPatientCare
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.features.patient.appointments.AppointmentsViewModel
import com.luismejias.lumemedlink.features.patient.doctors.MyDoctorsViewModel
import com.luismejias.lumemedlink.features.patient.home.HomeContent
import com.luismejias.lumemedlink.features.patient.home.HomeViewModel
import com.luismejias.lumemedlink.features.patient.parts.awaitingConfirmation
import com.luismejias.lumemedlink.features.patient.parts.cancelled
import com.luismejias.lumemedlink.features.patient.parts.past
import com.luismejias.lumemedlink.features.patient.parts.upcoming
import com.luismejias.lumemedlink.features.patient.settings.settingsModel
import com.luismejias.lumemedlink.shared.CalendarDate
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Synthetic only (§9): the design's own sample people, none real.
@OptIn(ExperimentalCoroutinesApi::class)
class PatientTabsTest {
    private val design = ScriptedPatientCare(latency = {})

    @BeforeTest
    fun mainDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun resetDispatcher() = Dispatchers.resetMain()

    @Test
    fun inicioLeadsWithTheConfirmedAppointmentAndListsTheRequestApart() = runTest {
        val home = HomeViewModel(design).also { it.load() }
        val content = assertIs<CareLoad.Ready<HomeContent>>(home.state.value).content
        assertEquals("p-rendon", content.next?.doctor?.id)
        assertEquals(listOf("p-pinto"), content.waiting.map { it.doctor.id })
        assertEquals("p-fuentes", content.rating?.doctor?.id)
        assertEquals("Camila", content.givenNames)
    }

    @Test
    fun inicioWithNothingBookedHasNoHeroCard() = runTest {
        val home = HomeViewModel(ScriptedPatientCare(world = CareWorld.EMPTY, latency = {})).also { it.load() }
        val content = (home.state.value as CareLoad.Ready).content
        assertNull(content.next)
        assertTrue(content.waiting.isEmpty())
        assertNull(content.rating)
    }

    @Test
    fun offlineIsTheStateNotAnEmptyScreen() = runTest {
        val offline = ScriptedPatientCare(failure = CareFailure.Offline, latency = {})
        assertEquals(CareLoad.Failed(CareFailure.Offline), HomeViewModel(offline).also { it.load() }.state.value)
        val appointments = AppointmentsViewModel(offline).also { it.load() }
        assertEquals(CareLoad.Failed(CareFailure.Offline), appointments.state.value)
        assertEquals(CareLoad.Failed(CareFailure.Offline), MyDoctorsViewModel(offline).also { it.load() }.state.value)
        assertEquals(
            CareLoad.Failed(CareFailure.Offline),
            settingsModel(offline).also {
                it.load()
            }.state.value,
        )
    }

    @Test
    fun theBadgeCountsOnlyRequestsStillWaiting() = runTest {
        val content = (AppointmentsViewModel(design).also { it.load() }.state.value as CareLoad.Ready).content
        assertEquals(1, content.awaiting)
        assertEquals(2, content.upcoming.size)
        assertEquals(listOf(AppointmentStatus.COMPLETED, AppointmentStatus.COMPLETED), content.past.map { it.status })
        assertEquals(24, content.changeWindowHours)
    }

    @Test
    fun theDoctorFilterIgnoresCaseAndAccentsAndReadsTheSpecialty() = runTest {
        val doctors = MyDoctorsViewModel(design).also { it.load() }
        doctors.queryChanged("RENDON")
        assertEquals(listOf("p-rendon"), ready(doctors).shown.map { it.doctor.id })
        doctors.queryChanged("dermato")
        assertEquals(listOf("p-pinto"), ready(doctors).shown.map { it.doctor.id })
        doctors.queryChanged("nadie")
        assertTrue(ready(doctors).shown.isEmpty())
        doctors.load()
        assertEquals("nadie", ready(doctors).query, "a retry keeps what was typed")
    }

    @Test
    fun aDoctorWhoSharesNothingSaysSo() = runTest {
        val all = ready(MyDoctorsViewModel(design).also { it.load() }).all
        assertFalse(all.single { it.doctor.id == "p-pinto" }.sharesContact)
        assertTrue(all.single { it.doctor.id == "p-fuentes" }.sharesContact)
    }

    @Test
    fun remindersReadLargestFirstAndNothingPersonalPrints() = runTest {
        val settings = settingsModel(design).also {
            it.load()
        }
        val content = (settings.state.value as CareLoad.Ready).content
        assertEquals(listOf(24, 2), content.remindersHoursBefore)
        assertFalse("Camila" in content.toString())
        assertFalse("Soto" in content.profile.toString())
    }

    @Test
    fun aCancelledOrAttendedAppointmentIsNeverUpcoming() {
        val today = CalendarDate(2026, 9, 16)
        fun at(id: String, offset: Int, status: AppointmentStatus) = PatientAppointment(
            id,
            DOCTOR,
            today.plusDays(offset),
            ClockTime(10, 0),
            30,
            status,
            PLACE,
        )
        val all = listOf(
            at("cancelled-ahead", 3, AppointmentStatus.CANCELLED),
            at("attended-today", 0, AppointmentStatus.COMPLETED),
            at("requested", 2, AppointmentStatus.REQUESTED),
            at("confirmed", 1, AppointmentStatus.CONFIRMED),
            at("yesterday", -1, AppointmentStatus.CONFIRMED),
        )
        assertEquals(listOf("confirmed", "requested"), all.upcoming(today).map { it.id })
        assertEquals(listOf("attended-today", "yesterday"), all.past(today).map { it.id })
        assertEquals(listOf("cancelled-ahead"), all.cancelled().map { it.id }, "Canceladas is its own tab (e8)")
        assertEquals(listOf("requested"), all.awaitingConfirmation(today).map { it.id })
    }

    private fun ready(model: MyDoctorsViewModel) = (model.state.value as CareLoad.Ready).content

    private companion object {
        val DOCTOR = CareDoctor("p-x", "Dra.", "Ana", "Prueba", "Medicina general")
        val PLACE = CarePlace("Calle 1", "Centro · Comuna")
    }
}
