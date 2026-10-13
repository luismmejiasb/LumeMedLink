package com.luismejias.lumemedlink.core.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyticsCatalogTest {
    /** Firebase's own rule for an event name: a letter first, then letters, digits and underscores, 40 at most. */
    private val firebaseName = Regex("^[a-z][a-z0-9_]{0,39}$")

    @Test
    fun everyNameIsOneFirebaseAccepts() {
        AnalyticsEvent.entries.forEach { assertTrue(firebaseName.matches(it.eventName), it.eventName) }
    }

    @Test
    fun noTwoEventsShareAName() {
        assertEquals(AnalyticsEvent.entries.size, AnalyticsEvent.entries.map { it.eventName }.toSet().size)
    }

    @Test
    fun noNameUsesFirebasesReservedPrefixes() {
        val reserved = listOf("firebase_", "google_", "ga_")
        AnalyticsEvent.entries.forEach { event -> reserved.forEach { assertTrue(!event.eventName.startsWith(it)) } }
    }
}
