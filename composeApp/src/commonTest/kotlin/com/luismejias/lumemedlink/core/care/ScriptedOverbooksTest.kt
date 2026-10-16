package com.luismejias.lumemedlink.core.care

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The script plays the platform's rules for overbook requests (ADR-0069), so the screens can be built against it. */
class ScriptedOverbooksTest {
    private fun script(world: CareWorld = CareWorld.DESIGN) = ScriptedPatientCare(world, latency = {})

    private val ask = OverbookDraft("p-rendon", OverbookUrgency.NORMAL, OverbookReason.EARLIER_FOLLOW_UP)

    @Test
    fun theDesignHasOneRecordedCallAndAnEmptyWorldNone() = runTest {
        val list = assertIs<CareResult.Ok<List<OverbookRequest>>>(script().overbookRequests()).value
        assertEquals(listOf(true), list.map { it.byPhone })
        assertEquals(CareResult.Ok(emptyList()), script(CareWorld.EMPTY).overbookRequests())
    }

    @Test
    fun onlyAProfessionalWhoOpenedItCanBeAskedAndOnlyOnceAtATime() = runTest {
        val care = script()
        assertEquals(
            OverbookResult.Failed(OverbookFailure.NotTaken),
            care.requestOverbook(ask.copy(doctorId = "p-contreras"), "k-0"),
        )
        val asked = assertIs<OverbookResult.Done>(care.requestOverbook(ask, "k-1")).request
        assertTrue(asked.waiting)
        assertEquals(OverbookResult.Done(asked), care.requestOverbook(ask, "k-1"), "a retry answers the same request")
        assertEquals(OverbookResult.Failed(OverbookFailure.AlreadyWaiting), care.requestOverbook(ask, "k-2"))
        val listed = assertIs<CareResult.Ok<List<OverbookRequest>>>(care.overbookRequests()).value
        assertEquals(asked.id, listed.first().id, "newest first")
    }

    @Test
    fun onlyAWaitingRequestCanBeTakenBack() = runTest {
        val care = script()
        val asked = assertIs<OverbookResult.Done>(care.requestOverbook(ask, "k-1")).request
        val withdrawn = assertIs<OverbookResult.Done>(care.withdrawOverbookRequest(asked)).request
        assertEquals(OverbookRequestStatus.WITHDRAWN, withdrawn.status)
        assertEquals(OverbookResult.Failed(OverbookFailure.NoLongerWaiting), care.withdrawOverbookRequest(asked))
    }
}
