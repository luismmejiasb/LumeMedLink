package com.luismejias.lumemedlink.features.patient.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The screens of the Buscar tab's stack, from the search to the booking (p2 → a2). */
internal enum class SearchRoute {
    ROOT,
    SPECIALTY,
    COMMUNE,
    RESULTS,
    FILTERS,
    AVAILABILITY,
    SCHEDULE,
    CONFIRM,
    ;

    /** Whether the tab bar shows under it: not under p4 and its list, nor under pinned actions (a1, a2). */
    val showsTabBar: Boolean get() = this !in setOf(FILTERS, AVAILABILITY, SCHEDULE, CONFIRM)
}

internal enum class SearchMove { FORWARD, BACK }

/** The Buscar tab's stack (ADR-0045: one stack per tab). Pure Kotlin: a test drives it without a screen. */
internal class SearchFlow {
    private val path = MutableStateFlow(listOf(SearchRoute.ROOT))
    private val move = MutableStateFlow(SearchMove.FORWARD)

    val routes: StateFlow<List<SearchRoute>> = path.asStateFlow()
    val lastMove: StateFlow<SearchMove> = move.asStateFlow()

    val top: SearchRoute get() = path.value.last()

    fun push(route: SearchRoute) {
        move.value = SearchMove.FORWARD
        path.value = path.value + route
    }

    /** One step back; the root has nowhere to go. */
    fun back(): Boolean {
        if (path.value.size <= 1) return false
        move.value = SearchMove.BACK
        path.value = path.value.dropLast(1)
        return true
    }

    /** Back to [route] if it is in the stack, dropping everything above it. */
    fun backTo(route: SearchRoute): Boolean {
        val index = path.value.lastIndexOf(route)
        if (index < 0 || index == path.value.lastIndex) return false
        move.value = SearchMove.BACK
        path.value = path.value.take(index + 1)
        return true
    }

    fun reset() {
        move.value = SearchMove.BACK
        path.value = listOf(SearchRoute.ROOT)
    }
}
