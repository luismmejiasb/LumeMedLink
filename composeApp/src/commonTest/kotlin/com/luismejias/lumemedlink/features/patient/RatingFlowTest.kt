package com.luismejias.lumemedlink.features.patient

import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.RatingAspect
import com.luismejias.lumemedlink.core.care.ScriptedPatientCare
import com.luismejias.lumemedlink.features.patient.rating.RateViewModel
import com.luismejias.lumemedlink.features.patient.rating.RatingLevel
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
    fun otherHoldsFortyCharactersAndGoesAwayWithItsMark() = runTest {
        model.open(prompt(), 3)
        model.otherToggled()
        model.otherChanged("x".repeat(60))
        assertEquals(40, model.state.value?.other?.length)
        model.otherToggled()
        assertEquals("", model.state.value?.other, "unmarking «Otro» discards its words")
    }

    @Test
    fun aSentRatingLeavesInicioAndShowsOnTheVisit() = runTest {
        val prompt = prompt()
        model.open(prompt, 5)
        model.commentChanged("Muy clara y puntual.")
        model.send()
        assertNull(model.state.value)
        assertTrue(model.sent.value)
        assertTrue((script.ratingPrompts() as CareResult.Ok).value.isEmpty())
        assertEquals(5, (script.ratingFor(prompt.appointmentId) as CareResult.Ok).value?.stars)
    }
}
