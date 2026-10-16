package com.luismejias.lumemedlink.features.patient

import com.luismejias.lumemedlink.core.care.CareFailure
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.OverbookDraft
import com.luismejias.lumemedlink.core.care.OverbookFailure
import com.luismejias.lumemedlink.core.care.OverbookReason
import com.luismejias.lumemedlink.core.care.OverbookRequest
import com.luismejias.lumemedlink.core.care.OverbookRequestStatus
import com.luismejias.lumemedlink.core.care.OverbookRequests
import com.luismejias.lumemedlink.core.care.OverbookResult
import com.luismejias.lumemedlink.core.care.OverbookUrgency
import com.luismejias.lumemedlink.core.care.ScriptedPatientCare
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.features.patient.appointments.AppointmentsContent
import com.luismejias.lumemedlink.features.patient.appointments.AppointmentsViewModel
import com.luismejias.lumemedlink.features.patient.appointments.RECENT_OVERBOOK_DAYS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** «Solicitudes de sobrecupo» on «Citas» (ADR-0069): what the section lists, and taking one back. */
@OptIn(ExperimentalCoroutinesApi::class)
class OverbookListTest {
    @BeforeTest
    fun mainDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun resetDispatcher() = Dispatchers.resetMain()

    private val ask = OverbookDraft("p-rendon", OverbookUrgency.NORMAL, OverbookReason.OTHER)

    private suspend fun AppointmentsViewModel.content(): AppointmentsContent =
        assertIs<CareLoad.Ready<AppointmentsContent>>(also { it.load() }.state.value).content

    @Test
    fun theSectionListsWhatWaitsAndWhatWasAnsweredLately() = runTest {
        val design = ScriptedPatientCare(latency = {})
        val old = OverbookRequest(
            "o-old",
            null,
            "",
            OverbookRequestStatus.DECLINED,
            design.today().plusDays(-RECENT_OVERBOOK_DAYS - 1),
            true,
        )
        val waitingOld = old.copy(id = "o-waiting", status = OverbookRequestStatus.PENDING)
        val requests = object : OverbookRequests by design {
            override suspend fun overbookRequests() = CareResult.Ok(listOf(waitingOld, old))
        }
        val content = AppointmentsViewModel(design, design, requests).content()
        assertEquals(listOf("o-waiting"), content.overbooks.map { it.id }, "an old answer goes; a wait never does")
    }

    @Test
    fun takingOneBackReadsTheListAgain() = runTest {
        val design = ScriptedPatientCare(latency = {})
        val asked = assertIs<OverbookResult.Done>(design.requestOverbook(ask, "k-1")).request
        val model = AppointmentsViewModel(design, design, design)
        model.load()
        model.withdrawOverbook(asked)
        advanceUntilIdle()
        val content = assertIs<CareLoad.Ready<AppointmentsContent>>(model.state.value).content
        assertEquals(OverbookRequestStatus.WITHDRAWN, content.overbooks.first { it.id == asked.id }.status)
        assertNull(model.withdrawalFailure.value)
    }

    @Test
    fun aFailedWithdrawalIsSaidAndOneAnsweredFirstIsJustReadAgain() = runTest {
        val design = ScriptedPatientCare(latency = {})
        val asked = assertIs<OverbookResult.Done>(design.requestOverbook(ask, "k-1")).request
        val offline = ScriptedPatientCare(failure = CareFailure.Offline, latency = {})
        val model = AppointmentsViewModel(design, design, offline)
        model.withdrawOverbook(asked)
        advanceUntilIdle()
        assertEquals(OverbookFailure.Offline, model.withdrawalFailure.value)

        design.withdrawOverbookRequest(asked)
        val answered = AppointmentsViewModel(design, design, design)
        answered.withdrawOverbook(asked)
        advanceUntilIdle()
        assertNull(answered.withdrawalFailure.value, "no longer waiting: no error, the list says how it ended")
    }
}
