package com.luismejias.lumemedlink.features.patient.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cl.lume.uicomposer.components.LumeAlert
import cl.lume.uicomposer.components.LumeAlertAction
import cl.lume.uicomposer.components.LumeAsyncButton
import cl.lume.uicomposer.components.LumeButton
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeButtonEmphasis
import cl.lume.uicomposer.components.LumeButtonFeedback
import cl.lume.uicomposer.components.LumeButtonWidth
import cl.lume.uicomposer.components.LumeCalloutBox
import cl.lume.uicomposer.components.LumeCalloutStyle
import cl.lume.uicomposer.components.LumeCard
import cl.lume.uicomposer.components.LumeEmptyState
import cl.lume.uicomposer.components.LumeEqualRow
import cl.lume.uicomposer.components.LumeEyebrow
import cl.lume.uicomposer.components.LumeFieldState
import cl.lume.uicomposer.components.LumeList
import cl.lume.uicomposer.components.LumeListAccessory
import cl.lume.uicomposer.components.LumeListRow
import cl.lume.uicomposer.components.LumeListStyle
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeStackAxis
import cl.lume.uicomposer.components.LumeStackStyle
import cl.lume.uicomposer.components.LumeToggleGroup
import cl.lume.uicomposer.components.LumeToggleGroupLayout
import cl.lume.uicomposer.components.LumeToggleGroupSelection
import cl.lume.uicomposer.foundations.LumeIcon
import cl.lume.uicomposer.tokens.LumeStatusStyle
import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.BookingFailure
import com.luismejias.lumemedlink.core.care.DirectoryDoctor
import com.luismejias.lumemedlink.core.care.Slot
import com.luismejias.lumemedlink.features.patient.parts.CareLoad
import com.luismejias.lumemedlink.features.patient.parts.CareLoadView
import com.luismejias.lumemedlink.features.patient.parts.PushedScreen
import com.luismejias.lumemedlink.features.patient.parts.capitalized
import com.luismejias.lumemedlink.features.patient.parts.doctorMarker
import com.luismejias.lumemedlink.features.patient.parts.longDateText
import com.luismejias.lumemedlink.features.patient.parts.longWeekdayDayText
import com.luismejias.lumemedlink.features.patient.parts.weekdayDayText
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.booked_body
import com.luismejias.lumemedlink.resources.booked_title
import com.luismejias.lumemedlink.resources.booking_offline_body
import com.luismejias.lumemedlink.resources.booking_offline_title
import com.luismejias.lumemedlink.resources.booking_unavailable_body
import com.luismejias.lumemedlink.resources.booking_unavailable_title
import com.luismejias.lumemedlink.resources.common_close
import com.luismejias.lumemedlink.resources.common_retry
import com.luismejias.lumemedlink.resources.confirm_action
import com.luismejias.lumemedlink.resources.confirm_back
import com.luismejias.lumemedlink.resources.confirm_duration
import com.luismejias.lumemedlink.resources.confirm_duration_value
import com.luismejias.lumemedlink.resources.confirm_note
import com.luismejias.lumemedlink.resources.confirm_place
import com.luismejias.lumemedlink.resources.confirm_running
import com.luismejias.lumemedlink.resources.confirm_title
import com.luismejias.lumemedlink.resources.confirm_when
import com.luismejias.lumemedlink.resources.confirm_when_value
import com.luismejias.lumemedlink.resources.pending_with_body
import com.luismejias.lumemedlink.resources.pending_with_title
import com.luismejias.lumemedlink.resources.schedule_afternoon
import com.luismejias.lumemedlink.resources.schedule_cancel
import com.luismejias.lumemedlink.resources.schedule_clash
import com.luismejias.lumemedlink.resources.schedule_continue
import com.luismejias.lumemedlink.resources.schedule_days
import com.luismejias.lumemedlink.resources.schedule_morning
import com.luismejias.lumemedlink.resources.schedule_none
import com.luismejias.lumemedlink.resources.schedule_title
import com.luismejias.lumemedlink.resources.sent_body
import com.luismejias.lumemedlink.resources.sent_done
import com.luismejias.lumemedlink.resources.sent_title
import com.luismejias.lumemedlink.resources.sent_view
import com.luismejias.lumemedlink.resources.taken_action
import com.luismejias.lumemedlink.resources.taken_body
import com.luismejias.lumemedlink.resources.taken_title
import org.jetbrains.compose.resources.stringResource

/** The doctor at the head of a1 and a2. */
@Composable
private fun DoctorHeader(entry: DirectoryDoctor) {
    LumeList(items = listOf(entry), key = { it.doctor.id }, scrolls = false) {
        LumeListRow(
            title = it.doctor.displayName,
            description = "${it.doctor.specialty} · ${it.commune}",
            leading = doctorMarker(it.doctor),
        )
    }
}

/**
 * a1. Only free hours are drawn — the design's struck-out taken ones are not in what availability returns, and
 * an hour the patient already holds elsewhere is left out with a line saying why. PROVISIONAL (ADR-0046): the
 * days are the kit's chips, without the design's month and year menus; the horizon is the next month.
 */
@Composable
internal fun ScheduleScreen(model: SearchViewModel, onBack: () -> Unit) {
    val load by model.schedule.state.collectAsState()
    val ready = (load as? CareLoad.Ready)?.content
    PushedScreen(
        title = stringResource(Res.string.schedule_title),
        onBack = onBack,
        action = {
            LumeEqualRow {
                LumeButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.schedule_cancel)),
                    emphasis = LumeButtonEmphasis.OutlineNeutral,
                    width = LumeButtonWidth.Fill,
                    onClick = onBack,
                )
                LumeButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.schedule_continue)),
                    width = LumeButtonWidth.Fill,
                    state = if (ready?.slot != null) LumeFieldState.Enabled else LumeFieldState.Disabled,
                    onClick = { ready?.slot?.let { model.confirm(ready.doctor, it) } },
                )
            }
        },
    ) {
        CareLoadView(load, onRetry = {
            ready?.doctor?.let { doctor -> model.launch { schedule.load(doctor) } }
        }) { content ->
            DoctorHeader(content.doctor)
            val day = content.day
            if (day == null) {
                LumeEmptyState(icon = LumeIcon.Calendar, title = stringResource(Res.string.schedule_none))
                return@CareLoadView
            }
            LumeEyebrow(stringResource(Res.string.schedule_days))
            val dayLabels = content.days.associateWith { weekdayDayText(it).capitalized() }
            LumeStack(axis = LumeStackAxis.Horizontal, style = LumeStackStyle.Overflow) {
                LumeToggleGroup(
                    items = content.days,
                    selection = LumeToggleGroupSelection.Exclusive(day, model.schedule::dayChosen),
                    label = { dayLabels.getValue(it) },
                    layout = LumeToggleGroupLayout.HorizontalHug,
                )
            }
            val selection = LumeToggleGroupSelection.Single(content.slot, model.schedule::slotChosen)
            if (content.morning.isNotEmpty()) {
                LumeEyebrow(stringResource(Res.string.schedule_morning, longWeekdayDayText(day)))
                SlotGrid(content.morning, selection)
            }
            if (content.afternoon.isNotEmpty()) {
                LumeEyebrow(stringResource(Res.string.schedule_afternoon))
                SlotGrid(content.afternoon, selection)
            }
            content.clashes.forEach { clash ->
                LumeCalloutBox(
                    text = stringResource(Res.string.schedule_clash, clash.start.display, clash.doctor.displayName),
                    style = LumeCalloutStyle.Warning,
                ) {}
            }
        }
    }
}

@Composable
private fun SlotGrid(slots: List<Slot>, selection: LumeToggleGroupSelection<Slot>) {
    LumeToggleGroup(
        items = slots,
        selection = selection,
        label = { it.start.display },
        layout = LumeToggleGroupLayout.Wrapped,
    )
}

/**
 * a2, with a3 and the refusals as alerts over it. There is no «Motivo de la consulta»: the author removed it,
 * and a clinical reason has no place in this app (ADR-0001). No «Previsión» either: the contract books without
 * one. The confirmation is the kit's async button — one tap, one request, its key reused by any retry.
 */
@Composable
internal fun ConfirmScreen(
    model: SearchViewModel,
    onBack: () -> Unit,
    onSeeAppointments: (AppointmentStatus) -> Unit,
    onDone: (AppointmentStatus) -> Unit,
) {
    val booking by model.booking.state.collectAsState()
    val state = booking ?: return
    val doctor = state.doctor
    val slot = state.slot
    val date = longDateText(slot.date)
    PushedScreen(
        title = stringResource(Res.string.confirm_title),
        onBack = onBack,
        action = {
            LumeStack(alignment = LumeStackAlignment.Fill, spacing = { small }) {
                LumeAsyncButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.confirm_action)),
                    width = LumeButtonWidth.Fill,
                    feedback = LumeButtonFeedback(loading = stringResource(Res.string.confirm_running)),
                    run = model.booking::book,
                )
                LumeButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.confirm_back)),
                    emphasis = LumeButtonEmphasis.OutlineNeutral,
                    width = LumeButtonWidth.Fill,
                    onClick = onBack,
                )
            }
        },
    ) {
        DoctorHeader(doctor)
        LumeCard(modifier = Modifier.fillMaxWidth()) {
            LumeList(
                items = listOf(0, 1, 2),
                key = { it },
                style = LumeListStyle.Embedded,
            ) { row ->
                when (row) {
                    0 -> LumeListRow(
                        title = stringResource(Res.string.confirm_when_value, date, slot.start.display),
                        description = stringResource(Res.string.confirm_when),
                        leading = LumeListAccessory.Icon(LumeIcon.Calendar, ""),
                    )
                    1 -> LumeListRow(
                        title = stringResource(Res.string.confirm_duration_value, slot.minutes),
                        description = stringResource(Res.string.confirm_duration),
                        leading = LumeListAccessory.Icon(LumeIcon.Clock, ""),
                    )
                    else -> LumeListRow(
                        title = doctor.place.address,
                        description = "${stringResource(Res.string.confirm_place)} · ${doctor.place.detail}",
                        leading = LumeListAccessory.Icon(LumeIcon.Pin, ""),
                    )
                }
            }
        }
        LumeCalloutBox(text = stringResource(Res.string.confirm_note, doctor.doctor.displayName)) {}
    }
    when (val outcome = state.outcome) {
        null -> Unit
        is BookingOutcome.Sent -> {
            val status = outcome.appointment.status
            val waiting = status == AppointmentStatus.REQUESTED
            LumeAlert(
                visible = true,
                style = LumeStatusStyle.Success,
                title = stringResource(if (waiting) Res.string.sent_title else Res.string.booked_title),
                message = stringResource(
                    if (waiting) Res.string.sent_body else Res.string.booked_body,
                    date,
                    slot.start.display,
                    doctor.doctor.displayName,
                ),
                confirm = LumeAlertAction(stringResource(Res.string.sent_view)) { onSeeAppointments(status) },
                cancel = LumeAlertAction(stringResource(Res.string.sent_done)) { onDone(status) },
            )
        }
        is BookingOutcome.Refused -> RefusalAlert(outcome.failure, slot, doctor, model)
    }
}

@Composable
private fun RefusalAlert(failure: BookingFailure, slot: Slot, doctor: DirectoryDoctor, model: SearchViewModel) {
    val close = LumeAlertAction(stringResource(Res.string.common_close)) { model.booking.outcomeSeen() }
    when (failure) {
        BookingFailure.SlotTaken -> LumeAlert(
            visible = true,
            style = LumeStatusStyle.Warning,
            title = stringResource(Res.string.taken_title),
            message = stringResource(Res.string.taken_body, slot.start.display),
            confirm = LumeAlertAction(stringResource(Res.string.taken_action)) { model.launch { chooseAnotherHour() } },
        )
        BookingFailure.AlreadyPending -> LumeAlert(
            visible = true,
            style = LumeStatusStyle.Warning,
            title = stringResource(Res.string.pending_with_title, doctor.doctor.displayName),
            message = stringResource(Res.string.pending_with_body),
            confirm = close,
        )
        BookingFailure.Offline -> LumeAlert(
            visible = true,
            style = LumeStatusStyle.Error,
            title = stringResource(Res.string.booking_offline_title),
            message = stringResource(Res.string.booking_offline_body),
            confirm = LumeAlertAction(stringResource(Res.string.common_retry)) {
                model.booking.outcomeSeen()
                model.launch { booking.book() }
            },
            cancel = close,
        )
        BookingFailure.Unavailable -> LumeAlert(
            visible = true,
            style = LumeStatusStyle.Error,
            title = stringResource(Res.string.booking_unavailable_title),
            message = stringResource(Res.string.booking_unavailable_body),
            confirm = close,
        )
    }
}
