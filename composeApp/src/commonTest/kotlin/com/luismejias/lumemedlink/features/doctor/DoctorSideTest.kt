package com.luismejias.lumemedlink.features.doctor

import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.CareFailure
import com.luismejias.lumemedlink.core.care.ChangeFailure
import com.luismejias.lumemedlink.core.care.ScriptedDoctorCare
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.features.doctor.agenda.AgendaViewModel
import com.luismejias.lumemedlink.features.doctor.patients.DoctorPatientsViewModel
import com.luismejias.lumemedlink.features.doctor.profile.DoctorProfileViewModel
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

// Synthetic only (§9): the design's sample patients.
@OptIn(ExperimentalCoroutinesApi::class)
class DoctorSideTest {
    private val script = ScriptedDoctorCare(latency = {})

    @BeforeTest
    fun mainDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun resetDispatcher() = Dispatchers.resetMain()

    @Test
    fun theAgendaOpensOnTodayWithTheWeekAndTheRequests() = runTest {
        val agenda = AgendaViewModel(script).also { it.load() }
        val content = (agenda.state.value as CareLoad.Ready).content
        assertEquals(7, content.entries.size, "the design's «Hoy · 7 citas»")
        assertEquals(3, content.requests.size)
        assertEquals(1, content.week.first().isoWeekday, "the week starts on Monday (L 14)")
        assertEquals(7, content.week.size)
    }

    @Test
    fun aConfirmedRequestLeavesTheListAndLandsOnItsDay() = runTest {
        val agenda = AgendaViewModel(script).also { it.load() }
        val victor = (agenda.state.value as CareLoad.Ready).content.requests.first()
        agenda.confirm(victor)
        assertEquals(2, (agenda.state.value as CareLoad.Ready).content.requests.size)
        agenda.dayChosen(victor.date)
        val monday = (agenda.state.value as CareLoad.Ready).content.entries
        assertEquals(AppointmentStatus.CONFIRMED, monday.first { it.id == victor.id }.status)
    }

    @Test
    fun aRejectedRequestIsGoneFromBoth() = runTest {
        val agenda = AgendaViewModel(script).also { it.load() }
        val victor = (agenda.state.value as CareLoad.Ready).content.requests.first()
        agenda.reject(victor)
        agenda.dayChosen(victor.date)
        val content = (agenda.state.value as CareLoad.Ready).content
        assertFalse(content.requests.any { it.id == victor.id })
        assertFalse(content.entries.any { it.id == victor.id })
    }

    @Test
    fun offlineAnAnswerIsRefusedAndSaid() = runTest {
        val agenda = AgendaViewModel(script).also { it.load() }
        val victor = (agenda.state.value as CareLoad.Ready).content.requests.first()
        val offline = AgendaViewModel(ScriptedDoctorCare(failure = CareFailure.Offline, latency = {}))
        offline.confirm(victor)
        assertEquals(ChangeFailure.Offline, offline.failure.value)
    }

    @Test
    fun patientsAreFoundByNameOrRutAndGroupedByInitial() = runTest {
        val patients = DoctorPatientsViewModel(script).also { it.load() }
        fun shown() = (patients.state.value as CareLoad.Ready).content
        assertEquals(listOf("A", "C", "D", "L", "P", "R", "T", "V"), shown().sections.map { it.first })
        patients.queryChanged("rivas")
        assertEquals(listOf("Ana Rivas"), shown().shown.map { it.displayName })
        patients.queryChanged("12.345")
        assertEquals(listOf("Ana Rivas"), shown().shown.map { it.displayName }, "a RUT typed with dots still matches")
        patients.queryChanged("nunez")
        assertEquals(listOf("Carla Núñez"), shown().shown.map { it.displayName }, "accents do not matter")
    }

    @Test
    fun theContactSwitchIsTheServersAfterARead() = runTest {
        val profile = DoctorProfileViewModel(script).also { it.load() }
        assertEquals(true, (profile.state.value as CareLoad.Ready).content.sharesContact)
        profile.shareContact(false)
        profile.load()
        assertEquals(false, (profile.state.value as CareLoad.Ready).content.sharesContact, "the write stuck")
    }
}
