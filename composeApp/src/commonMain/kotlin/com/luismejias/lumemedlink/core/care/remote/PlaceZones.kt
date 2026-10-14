package com.luismejias.lumemedlink.core.care.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.TimeZone

/**
 * Each place's zone, as the contract names it (since 0.130.0): learned from the appointments and the directory, and
 * asked for when an hour of that place is read or written. An hour shown on a place's wall clock goes back to UTC on
 * the SAME clock, or a move lands an hour off. A place the app has not seen answers [fallback] (ADR-0063).
 */
internal class PlaceZones(val fallback: TimeZone = TimeZone.of(CLINIC_ZONE_UNTIL_CONTRACT)) {
    private val known = MutableStateFlow<Map<String, TimeZone>>(emptyMap())

    fun learn(locationId: String?, zone: String?) {
        if (locationId.isNullOrBlank() || zone == null) return
        known.update { it + (locationId to zoneOf(zone, fallback)) }
    }

    fun of(locationId: String): TimeZone = known.value[locationId] ?: fallback
}
