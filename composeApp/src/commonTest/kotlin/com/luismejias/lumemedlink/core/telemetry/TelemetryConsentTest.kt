package com.luismejias.lumemedlink.core.telemetry

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelemetryConsentTest {
    private val store = MemorySecureStore()
    private val telemetry = RecordedTelemetry()
    private val consent = TelemetryConsent(store, telemetry)

    @Test
    fun withoutAnAnswerTheLaunchKeepsCollectionOff() = runTest {
        consent.restore()
        assertEquals(listOf(false), telemetry.collection)
        assertTrue(store.entries.isEmpty(), "nothing is written before the person says yes")
    }

    @Test
    fun aChoiceReachesFirebaseAtOnceAndSurvivesTheNextLaunch() = runTest {
        consent.setGranted(true)
        TelemetryConsent(store, telemetry).restore()
        assertEquals(listOf(true, true), telemetry.collection)

        consent.setGranted(false)
        assertFalse(consent.granted())
        assertEquals(false, telemetry.collection.last())
    }

    @Test
    fun theLogoutsWipeTurnsCollectionOffForTheNextPerson() = runTest {
        consent.setGranted(true)
        store.wipe()
        consent.restore()
        assertEquals(false, telemetry.collection.last(), "a shared phone must not carry one person's yes to the next")
    }
}
