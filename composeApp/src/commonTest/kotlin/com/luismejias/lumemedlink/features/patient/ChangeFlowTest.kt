package com.luismejias.lumemedlink.features.patient

import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.CareFailure
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.ChangeFailure
import com.luismejias.lumemedlink.core.care.ChangeResult
import com.luismejias.lumemedlink.core.care.ClosedKind
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.ScriptedPatientCare
import com.luismejias.lumemedlink.features.patient.change.CancelChoice
import com.luismejias.lumemedlink.features.patient.change.CancelOutcome
import com.luismejias.lumemedlink.features.patient.change.ChangeDone
import com.luismejias.lumemedlink.features.patient.change.ChangeRoute
import com.luismejias.lumemedlink.features.patient.change.ChangeSheet
import com.luismejias.lumemedlink.features.patient.change.ChangeViewModel
import com.luismejias.lumemedlink.features.patient.change.MoveOutcome
import com.luismejias.lumemedlink.features.patient.change.MoveTile
import com.luismejias.lumemedlink.features.patient.change.changeOptionsFor
import com.luismejias.lumemedlink.features.patient.parts.CareLoad
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Synthetic only (§9): the design's sample doctors.
@OptIn(ExperimentalCoroutinesApi::class)
class ChangeFlowTest {
    private val script = ScriptedPatientCare(latency = {})
    private val model = ChangeViewModel(script, script)

    @BeforeTest
    fun mainDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun resetDispatcher() = Dispatchers.resetMain()

    private suspend fun appointment(id: String): PatientAppointment =
        (script.appointments() as CareResult.Ok).value.first { it.id == id }

    private fun reschedule() = (model.reschedule.state.value as CareLoad.Ready).content

    @Test
    fun beforeTheDoctorsWindowBothChoicesAreOpenAndThePhoneIsNotOffered() = runTest {
        model.openOptions(appointment(RENDON))
        val options = assertIs<ChangeSheet.Options>(model.sheet.value).options
        assertTrue(options.canChange, "10:30 at 09:41 is 49 minutes ahead of a 30-minute window")
        assertTrue(options.canCancel)
        assertNull(options.practicePhone)
    }

    @Test
    fun insideTheWindowBothAreRefusedAndThePracticesPhoneIsOffered() = runTest {
        val soon = appointment(RENDON).copy(start = ClockTime(10, 0))
        val options = changeOptionsFor(soon, script.today(), script.now(), sharedPhone = "+56 2 2345 6789")
        assertFalse(options.canChange)
        assertFalse(options.canCancel)
        assertEquals("+56 2 2345 6789", options.practicePhone)
    }

    @Test
    fun aRequestNotYetAnsweredCanAlwaysBeWithdrawn() = runTest {
        val soon = appointment(PINTO).copy(date = script.today(), start = ClockTime(9, 50))
        assertTrue(changeOptionsFor(soon, script.today(), script.now(), null).canCancel)
    }

    @Test
    fun theStripNamesHolidaysAndDaysWithoutAttention() = runTest {
        model.changeTime(appointment(RENDON))
        assertEquals(listOf(ChangeRoute.RESCHEDULE), model.routes.value)
        val days = reschedule().days.associateBy { it.date }
        assertEquals(2, days.getValue(script.today()).free, "only 11:00 and 15:00 are left today (f3)")
        assertEquals(ClosedKind.NO_ATTENTION, days.getValue(CalendarDate(2026, 9, 17)).closed)
        assertEquals(ClosedKind.HOLIDAY, days.getValue(CalendarDate(2026, 9, 18)).closed)
        assertEquals(1, days.getValue(CalendarDate(2026, 9, 20)).free, "Sunday the 20th has one hour (f3)")
    }

    @Test
    fun anHourTakenWhileChoosingIsStruckAndAnotherOneMoves() = runTest {
        model.changeTime(appointment(RENDON))
        val monday = script.today().plusDays(5)
        model.reschedule.dayChosen(monday)
        val ten = reschedule().morning.first { it.slot.start == ClockTime(10, 0) }.slot
        model.reschedule.slotChosen(ten)
        model.reschedule.submit()
        assertEquals(MoveOutcome.Taken(ten), reschedule().outcome)
        assertIs<MoveTile.Taken>(reschedule().morning.first { it.slot == ten }, "e10: struck through where it was")
        assertNull(reschedule().slot, "the button goes back to «Elige una hora»")

        val eleven = reschedule().morning.first { it.slot.start == ClockTime(11, 0) }.slot
        model.reschedule.slotChosen(eleven)
        model.reschedule.submit()
        val moved = assertIs<MoveOutcome.Moved>(reschedule().outcome).appointment
        assertEquals(AppointmentStatus.REQUESTED, moved.status)
        assertEquals(AppointmentStatus.CONFIRMED, appointment(RENDON).status, "the original keeps its hour (f3)")
        model.moved(moved)
        assertIs<ChangeDone.Moved>(model.done.value)
    }

    @Test
    fun aRetryOfTheSameMoveNeverMovesTwice() = runTest {
        model.changeTime(appointment(RENDON))
        model.reschedule.dayChosen(script.today().plusDays(6))
        val slot = (reschedule().morning.first() as MoveTile.Free).slot
        model.reschedule.slotChosen(slot)
        model.reschedule.submit()
        val first = assertIs<MoveOutcome.Moved>(reschedule().outcome).appointment
        model.reschedule.outcomeSeen()
        model.reschedule.submit()
        val again = assertIs<MoveOutcome.Moved>(reschedule().outcome).appointment
        assertEquals(first.id, again.id, "the move's client key answers with the move already made")
    }

    @Test
    fun cancellingAConfirmedAppointmentSendsTheChosenReason() = runTest {
        model.cancelAppointment(appointment(RENDON))
        model.cancel.choiceMade(CancelChoice.NOT_AT_THAT_TIME)
        model.cancel.submit()
        assertEquals(CancelOutcome.Cancelled, model.cancel.state.value?.outcome)
        assertEquals(AppointmentStatus.CANCELLED, appointment(RENDON).status)
    }

    @Test
    fun aRequestIsWithdrawnRatherThanCancelled() = runTest {
        model.cancelAppointment(appointment(PINTO))
        model.cancel.submit()
        assertEquals(CancelOutcome.Cancelled, model.cancel.state.value?.outcome)
        assertEquals(AppointmentStatus.CANCELLED, appointment(PINTO).status)
    }

    @Test
    fun offlineACancelIsRefusedNowAndNeverQueued() = runTest {
        val offline = ScriptedPatientCare(failure = CareFailure.Offline, latency = {})
        val flow = ChangeViewModel(offline, offline)
        flow.cancelAppointment(appointment(RENDON))
        flow.cancel.submit()
        assertEquals(CancelOutcome.Refused(ChangeFailure.Offline), flow.cancel.state.value?.outcome)
    }

    @Test
    fun theDoctorsWindowRefusesALateCancel() = runTest {
        val late = appointment(RENDON).copy(start = ClockTime(10, 0))
        val result = script.cancel(late, CancelChoice.OTHER.reason)
        assertEquals(ChangeResult.Failed(ChangeFailure.WindowClosed), result)
    }

    @Test
    fun theDetailSitsUnderAChangeAndBackReturnsToIt() = runTest {
        model.openDetail(appointment(RENDON))
        val detail = (model.detail.state.value as CareLoad.Ready).content
        assertEquals("+56 2 2345 6789", detail.phone, "Dra. Rendón shares her phone (c1)")
        assertTrue(detail.options.canChange)
        model.changeTime(appointment(RENDON))
        assertEquals(listOf(ChangeRoute.DETAIL, ChangeRoute.RESCHEDULE), model.routes.value)
        model.back()
        assertEquals(listOf(ChangeRoute.DETAIL), model.routes.value)
        model.back()
        assertTrue(model.routes.value.isEmpty())
    }

    @Test
    fun anAttendedVisitShowsTheRatingAlreadySent() = runTest {
        model.openDetail(appointment(RENDON_ATTENDED))
        val rating = assertNotNull((model.detail.state.value as CareLoad.Ready).content.rating)
        assertEquals(5, rating.stars)
        assertNotNull(model.doctorToBookAgain(appointment(RENDON_ATTENDED)), "«Agendar de nuevo» finds her listed")
    }

    @Test
    fun choosingToChangeInsteadOfCancellingLeavesTheCancelBehind() = runTest {
        model.openDetail(appointment(RENDON))
        model.cancelAppointment(appointment(RENDON))
        model.changeInstead()
        assertEquals(listOf(ChangeRoute.DETAIL, ChangeRoute.RESCHEDULE), model.routes.value)
    }

    private companion object {
        const val RENDON = "a-rendon-0916"
        const val PINTO = "a-pinto-0921"
        const val RENDON_ATTENDED = "a-rendon-0902"
    }
}
