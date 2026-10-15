package com.luismejias.lumemedlink.features.patient

import com.luismejias.lumemedlink.core.care.CareFailure
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.RatingAspect
import com.luismejias.lumemedlink.core.care.RatingPrompt
import com.luismejias.lumemedlink.core.care.ScriptedPatientCare
import com.luismejias.lumemedlink.features.patient.rating.RateViewModel
import com.luismejias.lumemedlink.features.patient.rating.RatingLevel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Synthetic only (§9): the design's sample doctor.
@OptIn(ExperimentalCoroutinesApi::class)
class RatingFlowTest {
    private val script = ScriptedPatientCare(latency = {})
    private val model = RateViewModel(script)

    @BeforeTest
    fun mainDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun resetDispatcher() = Dispatchers.resetMain()

    private suspend fun prompt() = (script.ratingPrompts() as CareResult.Ok).value.single()

    @Test
    fun eachLevelAsksItsOwnQuestion() {
        assertEquals(RatingLevel.BAD, RatingLevel.of(2))
        assertEquals(RatingLevel.FAIR, RatingLevel.of(3))
        assertEquals(RatingLevel.GOOD, RatingLevel.of(5))
        assertTrue(RatingAspect.LONG_WAIT in RatingLevel.BAD.aspects)
        assertTrue(RatingAspect.SOLVED_IT in RatingLevel.GOOD.aspects)
    }

    @Test
    fun aNewLevelKeepsOnlyTheAspectsItAlsoOffers() = runTest {
        model.open(prompt(), 3)
        model.aspectToggled(RatingAspect.PUNCTUALITY)
        model.aspectToggled(RatingAspect.NOTICE)
        model.starsChanged(5)
        assertEquals(
            setOf(RatingAspect.PUNCTUALITY),
            model.state.value?.aspects,
            "punctuality is good too; notice is not",
        )
    }

    @Test
    fun aSentRatingLeavesInicioAndShowsOnTheVisit() = runTest {
        val prompt = prompt()
        model.open(prompt, 5)
        model.send()
        assertNull(model.state.value)
        assertTrue(model.sent.value)
        assertTrue((script.ratingPrompts() as CareResult.Ok).value.isEmpty())
        assertEquals(5, (script.ratingFor(prompt.appointmentId) as CareResult.Ok).value?.stars)
    }

    @Test
    fun aFailedSendKeepsTheSheetOpenAndSaysWhy() = runTest {
        val offline = RateViewModel(ScriptedPatientCare(failure = CareFailure.Offline, latency = {}))
        offline.open(prompt(), 4)

        offline.send()

        assertEquals(CareFailure.Offline, offline.state.value?.failure, "the sheet stays, with the failure inside it")
        assertFalse(offline.sent.value)
    }

    @Test
    fun anAnswerThatArrivesLateDoesNotCloseTheNextRating() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slow = RateViewModel(ScriptedPatientCare(latency = { gate.await() }))
        val first = prompt()
        slow.open(first, 4)
        val sending = launch { slow.send() }
        runCurrent()
        val next = RatingPrompt("a-otra", first.doctor, first.date)
        slow.dismiss()
        slow.open(next, 2)

        gate.complete(Unit)
        sending.join()

        assertEquals("a-otra", slow.state.value?.prompt?.appointmentId, "the second sheet stays open")
        assertNull(slow.state.value?.failure)
        assertTrue(slow.sent.value, "the first rating did go through")
    }
}
