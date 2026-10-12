package com.luismejias.lumemedlink.features.patient.change

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cl.lume.uicomposer.components.LumeAlert
import cl.lume.uicomposer.components.LumeAlertAction
import cl.lume.uicomposer.components.LumeAsyncButton
import cl.lume.uicomposer.components.LumeAvatarSize
import cl.lume.uicomposer.components.LumeButton
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeButtonEmphasis
import cl.lume.uicomposer.components.LumeButtonRole
import cl.lume.uicomposer.components.LumeButtonSize
import cl.lume.uicomposer.components.LumeButtonWidth
import cl.lume.uicomposer.components.LumeCallout
import cl.lume.uicomposer.components.LumeCalloutStyle
import cl.lume.uicomposer.components.LumeCalloutSurface
import cl.lume.uicomposer.components.LumeCard
import cl.lume.uicomposer.components.LumeCardStyle
import cl.lume.uicomposer.components.LumeDay
import cl.lume.uicomposer.components.LumeDayState
import cl.lume.uicomposer.components.LumeDayStrip
import cl.lume.uicomposer.components.LumeDayStripLayout
import cl.lume.uicomposer.components.LumeEmptyState
import cl.lume.uicomposer.components.LumeEyebrow
import cl.lume.uicomposer.components.LumeFieldState
import cl.lume.uicomposer.components.LumeList
import cl.lume.uicomposer.components.LumeListChrome
import cl.lume.uicomposer.components.LumeListRow
import cl.lume.uicomposer.components.LumeListSelection
import cl.lume.uicomposer.components.LumeListSelectionIndicator
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAxis
import cl.lume.uicomposer.components.LumeStackStyle
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTextRun
import cl.lume.uicomposer.components.LumeTextStyle
import cl.lume.uicomposer.components.LumeTimeSlot
import cl.lume.uicomposer.components.LumeTimeSlotGrid
import cl.lume.uicomposer.components.LumeTimeSlotState
import cl.lume.uicomposer.components.LumeToastHostState
import cl.lume.uicomposer.foundations.LumeIcon
import cl.lume.uicomposer.tokens.LumeStatusStyle
import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.ChangeFailure
import com.luismejias.lumemedlink.core.care.ClosedKind
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.Slot
import com.luismejias.lumemedlink.features.patient.parts.CareLoad
import com.luismejias.lumemedlink.features.patient.parts.CareLoadView
import com.luismejias.lumemedlink.features.patient.parts.DoctorIdentity
import com.luismejias.lumemedlink.features.patient.parts.PushedScreen
import com.luismejias.lumemedlink.features.patient.parts.capitalized
import com.luismejias.lumemedlink.features.patient.parts.longWeekdayDayText
import com.luismejias.lumemedlink.features.patient.parts.richRuns
import com.luismejias.lumemedlink.features.patient.parts.weekdayDayText
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.cancel_back
import com.luismejias.lumemedlink.resources.cancel_confirm
import com.luismejias.lumemedlink.resources.cancel_offline
import com.luismejias.lumemedlink.resources.cancel_reason_doctor
import com.luismejias.lumemedlink.resources.cancel_reason_note
import com.luismejias.lumemedlink.resources.cancel_reason_other
import com.luismejias.lumemedlink.resources.cancel_reason_time
import com.luismejias.lumemedlink.resources.cancel_reasons
import com.luismejias.lumemedlink.resources.cancel_title
import com.luismejias.lumemedlink.resources.cancel_unavailable
import com.luismejias.lumemedlink.resources.cancel_warning
import com.luismejias.lumemedlink.resources.cancel_window
import com.luismejias.lumemedlink.resources.common_close
import com.luismejias.lumemedlink.resources.day_holiday
import com.luismejias.lumemedlink.resources.day_hours
import com.luismejias.lumemedlink.resources.day_no_attention
import com.luismejias.lumemedlink.resources.day_spoken
import com.luismejias.lumemedlink.resources.months_long
import com.luismejias.lumemedlink.resources.move_failed_title
import com.luismejias.lumemedlink.resources.move_offline_body
import com.luismejias.lumemedlink.resources.move_unavailable_body
import com.luismejias.lumemedlink.resources.move_window_body
import com.luismejias.lumemedlink.resources.reschedule_afternoon
import com.luismejias.lumemedlink.resources.reschedule_choose
import com.luismejias.lumemedlink.resources.reschedule_current
import com.luismejias.lumemedlink.resources.reschedule_keep
import com.luismejias.lumemedlink.resources.reschedule_none
import com.luismejias.lumemedlink.resources.reschedule_note
import com.luismejias.lumemedlink.resources.reschedule_title
import com.luismejias.lumemedlink.resources.reschedule_to
import com.luismejias.lumemedlink.resources.schedule_morning
import com.luismejias.lumemedlink.resources.slot_taken_spoken
import com.luismejias.lumemedlink.resources.taken_left
import com.luismejias.lumemedlink.resources.taken_next
import com.luismejias.lumemedlink.resources.taken_none
import com.luismejias.lumemedlink.resources.taken_now_body
import com.luismejias.lumemedlink.resources.taken_now_title
import com.luismejias.lumemedlink.resources.weekdays_short
import com.luismejias.lumemedlink.shared.CalendarDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** The days f3 draws at once: the week the design shows, in equal tiles that do not scroll. */
private const val WEEK_SHOWN = 6

/**
 * f3 «Cambiar hora», with e10. ONE request to the contract's atomic move (T7). The original keeps its hour until the
 * doctor confirms the new one. PROVISIONAL (ADR-0046): taken hours are not drawn — availability returns free ones —
 * except one taken while the patient chose it, which is struck through where it was.
 */
@Composable
internal fun RescheduleScreen(model: ChangeViewModel, toasts: LumeToastHostState, today: CalendarDate) {
    val load by model.reschedule.state.collectAsState()
    val ready = (load as? CareLoad.Ready)?.content
    val takenTitle = stringResource(Res.string.taken_now_title)
    val takenBody = (ready?.outcome as? MoveOutcome.Taken)?.let {
        stringResource(Res.string.taken_now_body, it.slot.start.display)
    }
    LaunchedEffect(ready?.outcome) {
        when (val outcome = ready?.outcome) {
            is MoveOutcome.Taken -> {
                toasts.show(title = takenTitle, message = takenBody.orEmpty(), style = LumeStatusStyle.Warning)
                model.reschedule.outcomeSeen()
            }
            is MoveOutcome.Moved -> model.moved(outcome.appointment)
            else -> Unit
        }
    }
    PushedScreen(
        title = stringResource(Res.string.reschedule_title),
        onBack = model::back,
        action = { MoveActions(ready, model) },
    ) {
        CareLoadView(load, onRetry = {
            ready?.appointment?.let { appointment -> model.launch { reschedule.load(appointment) } }
        }) { content ->
            CurrentTime(content.appointment, today)
            val day = content.day
            if (day == null) {
                LumeEmptyState(icon = LumeIcon.Calendar, title = stringResource(Res.string.reschedule_none))
                return@CareLoadView
            }
            LumeEyebrow(stringArrayResource(Res.array.months_long)[day.month - 1])
            WeekStrip(content, model.reschedule)
            if (content.morning.isNotEmpty()) {
                LumeEyebrow(stringResource(Res.string.schedule_morning, longWeekdayDayText(day)))
                MoveGrid(content.morning, content.slot, model.reschedule::slotChosen)
            }
            if (content.afternoon.isNotEmpty()) {
                LumeEyebrow(stringResource(Res.string.reschedule_afternoon, longWeekdayDayText(day)))
                MoveGrid(content.afternoon, content.slot, model.reschedule::slotChosen)
            }
            if (content.taken.any { it.date == day }) TakenNote(content, day)
            if (content.appointment.status == AppointmentStatus.CONFIRMED) {
                LumeCallout(
                    text = stringResource(
                        Res.string.reschedule_note,
                        whenText(content.appointment, today).substringBefore(" · ").lowercase(),
                        content.appointment.doctor.displayName,
                    ),
                    icon = LumeIcon.Clock,
                    style = LumeCalloutStyle.Info,
                    surface = LumeCalloutSurface.Washed,
                )
            }
        }
    }
    (ready?.outcome as? MoveOutcome.Refused)?.let { refused ->
        LumeAlert(
            visible = true,
            style = LumeStatusStyle.Warning,
            title = stringResource(Res.string.move_failed_title),
            message = stringResource(
                when (refused.failure) {
                    ChangeFailure.WindowClosed -> Res.string.move_window_body
                    ChangeFailure.Offline -> Res.string.move_offline_body
                    else -> Res.string.move_unavailable_body
                },
            ),
            confirm = LumeAlertAction(stringResource(Res.string.common_close)) { model.reschedule.outcomeSeen() },
        )
    }
}

/**
 * The doctor, and the hour being given up struck through. The line is only a picture; the words «Hora actual» under
 * «Cambiar hora» say which hour it is.
 */
@Composable
private fun CurrentTime(appointment: PatientAppointment, today: CalendarDate) {
    val current = whenText(appointment, today).replaceFirstChar { it.lowercase() }
    LumeCard(modifier = Modifier.fillMaxWidth(), style = LumeCardStyle.Filled) {
        DoctorIdentity(appointment.doctor, emptyList(), avatar = LumeAvatarSize.Small) {
            LumeText(
                runs = listOf(
                    LumeTextRun.plain(stringResource(Res.string.reschedule_current)),
                    LumeTextRun.struck(current),
                ),
                style = LumeTextStyle.TextSmall,
                color = LumeTextColor.Secondary,
            )
        }
    }
}

@Composable
private fun WeekStrip(content: RescheduleContent, schedule: RescheduleModel) {
    val weekdays = stringArrayResource(Res.array.weekdays_short)
    val firstOpen = content.days.indexOfFirst { it.open }.coerceAtLeast(0)
    val start = (content.days.indexOfFirst { it.date == content.day }.takeIf { it >= 0 } ?: firstOpen)
        .let { it - it % WEEK_SHOWN }
    val week = content.days.drop(start).take(WEEK_SHOWN)
    val holiday = stringResource(Res.string.day_holiday)
    val closed = stringResource(Res.string.day_no_attention)
    LumeDayStrip(
        days = week.map { info ->
            val note = when {
                info.closed == ClosedKind.HOLIDAY -> holiday
                !info.open -> closed
                else -> pluralStringResource(Res.plurals.day_hours, info.free, info.free)
            }
            LumeDay(
                weekday = weekdays[info.date.isoWeekday - 1].capitalized(),
                day = info.date.day.toString(),
                contentDescription = stringResource(Res.string.day_spoken, longWeekdayDayText(info.date), note),
                state = when {
                    info.closed == ClosedKind.HOLIDAY -> LumeDayState.Holiday
                    !info.open -> LumeDayState.Unavailable
                    else -> LumeDayState.Available
                },
                note = note,
            )
        },
        selected = week.indexOfFirst { it.date == content.day }.takeIf { it >= 0 },
        onSelect = { schedule.dayChosen(week[it].date) },
        layout = LumeDayStripLayout.Fill,
    )
}

@Composable
private fun MoveGrid(tiles: List<MoveTile>, chosen: Slot?, onChoose: (Slot) -> Unit) {
    val spokenTaken = tiles.filterIsInstance<MoveTile.Taken>().associateWith {
        stringResource(Res.string.slot_taken_spoken, it.slot.start.display)
    }
    LumeTimeSlotGrid(
        slots = tiles.map { tile ->
            when (tile) {
                is MoveTile.Free -> LumeTimeSlot(tile.slot.start.display)
                is MoveTile.Taken -> LumeTimeSlot(
                    time = tile.slot.start.display,
                    state = LumeTimeSlotState.Taken,
                    contentDescription = spokenTaken.getValue(tile),
                )
            }
        },
        selected = tiles.indexOfFirst { it is MoveTile.Free && it.slot == chosen }.takeIf { it >= 0 },
        onSelect = { index -> (tiles[index] as? MoveTile.Free)?.let { onChoose(it.slot) } },
    )
}

/** e10: how many hours the day has left, and the next day that has some. */
@Composable
private fun TakenNote(content: RescheduleContent, day: CalendarDate) {
    val dayName = longWeekdayDayText(day).substringBefore(' ').lowercase()
    val left = if (content.freeToday == 0) {
        stringResource(Res.string.taken_none, dayName)
    } else {
        pluralStringResource(Res.plurals.taken_left, content.freeToday, content.freeToday, dayName)
    }
    val next = content.nextOpen?.let {
        " " + stringResource(Res.string.taken_next, longWeekdayDayText(it.date).lowercase(), it.free)
    }.orEmpty()
    LumeCallout(
        text = left + next,
        icon = LumeIcon.Clock,
        style = LumeCalloutStyle.Info,
        surface = LumeCalloutSurface.Washed,
    )
}

@Composable
private fun MoveActions(ready: RescheduleContent?, model: ChangeViewModel) {
    val slot = ready?.slot
    LumeStack(axis = LumeStackAxis.Horizontal, style = LumeStackStyle.Paired) {
        LumeButton(
            content = LumeButtonContent.Title(stringResource(Res.string.reschedule_keep)),
            emphasis = LumeButtonEmphasis.OutlineNeutral,
            size = LumeButtonSize.Large,
            width = LumeButtonWidth.Fill,
            onClick = model::back,
        )
        LumeAsyncButton(
            content = LumeButtonContent.Title(
                if (slot == null) {
                    stringResource(Res.string.reschedule_choose)
                } else {
                    stringResource(Res.string.reschedule_to, weekdayDayText(slot.date), slot.start.display)
                },
            ),
            size = LumeButtonSize.Large,
            width = LumeButtonWidth.Fill,
            state = if (slot != null) LumeFieldState.Enabled else LumeFieldState.Disabled,
            run = model.reschedule::submit,
        )
    }
}

/**
 * f4 «Cancelar cita». The reason is optional and only the doctor sees it; none of the three says anything about health
 * (ADR-0001). A request not yet confirmed is withdrawn. Never queued: offline the patient is told now.
 */
@Composable
internal fun CancelScreen(model: ChangeViewModel, today: CalendarDate) {
    val cancel by model.cancel.state.collectAsState()
    val state = cancel ?: return
    LaunchedEffect(state.outcome) { if (state.outcome == CancelOutcome.Cancelled) model.cancelled() }
    val labels = mapOf(
        CancelChoice.NOT_AT_THAT_TIME to stringResource(Res.string.cancel_reason_time),
        CancelChoice.ANOTHER_DOCTOR to stringResource(Res.string.cancel_reason_doctor),
        CancelChoice.OTHER to stringResource(Res.string.cancel_reason_other),
    )
    PushedScreen(
        title = stringResource(Res.string.cancel_title),
        onBack = model::back,
        action = {
            LumeStack(axis = LumeStackAxis.Horizontal, style = LumeStackStyle.Paired) {
                LumeButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.cancel_back)),
                    emphasis = LumeButtonEmphasis.OutlineNeutral,
                    size = LumeButtonSize.Large,
                    width = LumeButtonWidth.Fill,
                    onClick = model::back,
                )
                LumeAsyncButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.cancel_confirm)),
                    role = LumeButtonRole.Destructive,
                    size = LumeButtonSize.Large,
                    width = LumeButtonWidth.Fill,
                    run = model.cancel::submit,
                )
            }
        },
    ) {
        val appointment = state.appointment
        LumeCard(modifier = Modifier.fillMaxWidth(), style = LumeCardStyle.Filled) {
            DoctorIdentity(
                appointment.doctor,
                listOf("${whenText(appointment, today)} · ${appointment.doctor.specialty}"),
                avatar = LumeAvatarSize.Small,
            )
        }
        if (appointment.status != AppointmentStatus.REQUESTED) {
            LumeEyebrow(stringResource(Res.string.cancel_reasons))
            LumeList(
                items = CancelChoice.entries,
                key = { it },
                selection = LumeListSelection.Single(state.choice, model.cancel::choiceMade),
                scrolls = false,
                background = Color.Transparent,
                chrome = LumeListChrome.Open,
                selectionIndicator = LumeListSelectionIndicator.LeadingCircle,
            ) { LumeListRow(title = labels.getValue(it)) }
            LumeText(
                text = stringResource(Res.string.cancel_reason_note),
                style = LumeTextStyle.TextSmall,
                color = LumeTextColor.Secondary,
            )
        }
        LumeCallout(
            runs = stringResource(Res.string.cancel_warning).richRuns { model.launch { changeInstead() } },
            style = LumeCalloutStyle.Warning,
            surface = LumeCalloutSurface.Washed,
        )
        (state.outcome as? CancelOutcome.Refused)?.let { refused ->
            LumeCallout(
                text = stringResource(
                    when (refused.failure) {
                        ChangeFailure.Offline -> Res.string.cancel_offline
                        ChangeFailure.WindowClosed -> Res.string.cancel_window
                        else -> Res.string.cancel_unavailable
                    },
                ),
                style = LumeCalloutStyle.Error,
            )
        }
    }
}
