package com.luismejias.lumemedlink.features.patient.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cl.lume.uicomposer.components.LumeNavigationStack
import com.luismejias.lumemedlink.core.care.AppointmentStatus

/**
 * The Buscar tab's stack, drawn (ADR-0045). [onBooked] tells the tab host that an appointment now exists, and
 * whether the patient asked to see it in Mis citas.
 */
@Composable
internal fun SearchHost(
    model: SearchViewModel,
    onBooked: (status: AppointmentStatus, showAppointments: Boolean) -> Unit,
    /** Whether a result offers «Agendar» (ADR-0060). */
    canBook: Boolean,
) {
    val routes by model.flow.routes.collectAsState()
    val back: () -> Unit = { model.flow.back() }
    // The kit's stack draws push and pop as iOS does, with the edge's drag on iOS (kit 227a717).
    LumeNavigationStack(stack = routes, onBack = back) { route ->
        when (route) {
            SearchRoute.ROOT -> SearchScreen(
                model = model,
                onPickSpecialty = { model.launch { openSpecialty() } },
                onPickCommune = { model.launch { openCommune() } },
                onSearch = { model.launch { search() } },
            )
            SearchRoute.SPECIALTY -> SpecialtyScreen(model, back)
            SearchRoute.COMMUNE -> CommuneScreen(model, back)
            SearchRoute.RESULTS -> ResultsScreen(model, back, canBook)
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
