package com.luismejias.lumemedlink.features.patient

import com.luismejias.lumemedlink.core.care.CareFailure
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.DirectoryDoctor
import com.luismejias.lumemedlink.core.care.OverbookDraft
import com.luismejias.lumemedlink.core.care.OverbookFailure
import com.luismejias.lumemedlink.core.care.OverbookReason
import com.luismejias.lumemedlink.core.care.OverbookRequest
import com.luismejias.lumemedlink.core.care.OverbookRequests
import com.luismejias.lumemedlink.core.care.OverbookResult
import com.luismejias.lumemedlink.core.care.OverbookUrgency
import com.luismejias.lumemedlink.core.care.ScriptedPatientCare
import com.luismejias.lumemedlink.features.patient.overbook.OverbookViewModel
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Records every attempt's key, and refuses the first [failing] of them as offline. */
private class KeyedRequests(private val design: OverbookRequests, private var failing: Int = 0) :
    OverbookRequests by design {
    val keys = mutableListOf<String>()

    override suspend fun requestOverbook(draft: OverbookDraft, clientId: String): OverbookResult {
        keys += clientId
        if (failing > 0) {
            failing--
            return OverbookResult.Failed(OverbookFailure.Offline)
        }
        return design.requestOverbook(draft, clientId)
    }
}

/** «Pedir sobrecupo» (ADR-0069): what the sheet sends, and with which key. */
@OptIn(ExperimentalCoroutinesApi::class)
class OverbookAskTest {
    @BeforeTest
    fun mainDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun resetDispatcher() = Dispatchers.resetMain()

    private val design = ScriptedPatientCare(latency = {})

    private suspend fun rendon(): DirectoryDoctor {
        val found = design.doctor("p-rendon")
        return (found as CareResult.Ok).value!!
    }

    @Test
    fun nothingIsSentUntilTheUrgencyAndTheReasonAreChosen() = runTest {
        val requests = KeyedRequests(design)
        val model = OverbookViewModel(requests)
        model.open(rendon())
        model.urgencyChosen(OverbookUrgency.NORMAL)
        model.send()
        assertTrue(requests.keys.isEmpty())
        model.reasonChosen(OverbookReason.EARLIER_FOLLOW_UP)
        model.send()
        assertEquals(1, requests.keys.size)
        assertNull(model.state.value, "the sheet closes")
        assertTrue(model.sent.value)
    }

    @Test
    fun aRetryReusesItsKeyAndAnotherChoiceRenewsIt() = runTest {
        val requests = KeyedRequests(design, failing = 2)
        val model = OverbookViewModel(requests)
        model.open(rendon())
        model.urgencyChosen(OverbookUrgency.NORMAL)
        model.reasonChosen(OverbookReason.OTHER)
        model.send()
        assertEquals(OverbookFailure.Offline, model.state.value?.failure)
        model.send()
        assertEquals(requests.keys[0], requests.keys[1], "the same attempt, sent again")
        model.urgencyChosen(OverbookUrgency.PRIORITY)
        assertNull(model.state.value?.failure, "another choice is another attempt")
        model.send()
        assertNotEquals(requests.keys[1], requests.keys[2])
        assertNull(model.state.value)
    }

    @Test
    fun aProfessionalWithAWaitingRequestIsSaidAndTheSheetStays() = runTest {
        val model = OverbookViewModel(design)
        model.open(rendon())
        model.urgencyChosen(OverbookUrgency.NORMAL)
        model.reasonChosen(OverbookReason.OTHER)
        model.send()
        model.sentSeen()
        model.open(rendon())
        model.urgencyChosen(OverbookUrgency.URGENT)
        model.reasonChosen(OverbookReason.OTHER)
        model.send()
        assertEquals(OverbookFailure.AlreadyWaiting, model.state.value?.failure)
        assertFalse(model.sent.value)
    }

    @Test
    fun theSheetNeverPrintsWhatWasChosen() = runTest {
        val model = OverbookViewModel(design)
        model.open(rendon())
        model.urgencyChosen(OverbookUrgency.URGENT)
        model.reasonChosen(OverbookReason.RENEW_PRESCRIPTION)
        val printed = model.state.value.toString()
        listOf("URGENT", "RENEW", "p-rendon").forEach { assertFalse(it in printed, it) }
    }

    @Test
    fun theScriptListsWhatWasAsked() = runTest {
        val model = OverbookViewModel(design)
        model.open(rendon())
        model.urgencyChosen(OverbookUrgency.NORMAL)
        model.reasonChosen(OverbookReason.OTHER)
        model.send()
        val listed = (design.overbookRequests() as CareResult.Ok<List<OverbookRequest>>).value
        assertEquals("p-rendon", listed.first().doctor?.id)
        assertEquals(CareResult.Failed(CareFailure.Offline), ScriptedPatientCare(failure = CareFailure.Offline, latency = {}).overbookRequests())
    }
}
