package com.luismejias.lumemedlink.features.patient.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.IntOffset
import cl.lume.uicomposer.foundations.LumeTheme
import cl.lume.uicomposer.foundations.lumeAnimationsEnabled
import com.luismejias.lumemedlink.core.care.AppointmentStatus

/** How far a screen drifts as it enters: a fraction of the width, as the access flow moves. */
private const val DRIFT_DIVISOR = 6

/**
 * The Buscar tab's stack, drawn (ADR-0045). [onBooked] tells the tab host that an appointment now exists, and
 * whether the patient asked to see it in Mis citas.
 */
@Composable
internal fun SearchHost(
    model: SearchViewModel,
    onBooked: (status: AppointmentStatus, showAppointments: Boolean) -> Unit,
) {
    val routes by model.flow.routes.collectAsState()
    val move by model.flow.lastMove.collectAsState()
    val duration = LumeTheme.current.motion.appear
    val animates = lumeAnimationsEnabled()
    val back: () -> Unit = { model.flow.back() }
    AnimatedContent(
        targetState = routes.last(),
        transitionSpec = {
            val sign = if (move == SearchMove.FORWARD) 1 else -1
            if (!animates) {
                fadeIn(snap()) togetherWith fadeOut(snap())
            } else {
                (
                    fadeIn(tween(duration)) +
                        slideInHorizontally(tween<IntOffset>(duration)) { sign * it / DRIFT_DIVISOR }
                    ) togetherWith (
                    fadeOut(tween(duration)) +
                        slideOutHorizontally(tween<IntOffset>(duration)) { -sign * it / DRIFT_DIVISOR }
                    )
            }
        },
        label = "search-route",
    ) { route ->
        when (route) {
            SearchRoute.ROOT -> SearchScreen(
                model = model,
                onPickSpecialty = { model.launch { openSpecialty() } },
                onPickCommune = { model.launch { openCommune() } },
                onSearch = { model.launch { search() } },
            )
            SearchRoute.SPECIALTY -> SpecialtyScreen(model, back)
            SearchRoute.COMMUNE -> CommuneScreen(model, back)
            SearchRoute.RESULTS -> ResultsScreen(model, back)
            SearchRoute.FILTERS -> FiltersScreen(model, back)
            SearchRoute.AVAILABILITY -> AvailabilityScreen(model, back)
            SearchRoute.SCHEDULE -> ScheduleScreen(model, back)
            SearchRoute.CONFIRM -> ConfirmScreen(
                model = model,
                onBack = back,
                onSeeAppointments = { status ->
                    model.finishBooking()
                    onBooked(status, true)
                },
                onDone = { status ->
                    model.finishBooking()
                    onBooked(status, false)
                },
            )
        }
    }
}
