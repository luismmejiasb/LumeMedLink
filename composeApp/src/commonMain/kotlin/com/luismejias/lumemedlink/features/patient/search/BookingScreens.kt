package com.luismejias.lumemedlink.features.patient.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cl.lume.uicomposer.components.LumeAlert
import cl.lume.uicomposer.components.LumeAlertAction
import cl.lume.uicomposer.components.LumeAlertPresentation
import cl.lume.uicomposer.components.LumeAsyncButton
import cl.lume.uicomposer.components.LumeButton
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeButtonEmphasis
import cl.lume.uicomposer.components.LumeButtonFeedback
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
import cl.lume.uicomposer.components.LumeEmptyState
import cl.lume.uicomposer.components.LumeEyebrow
import cl.lume.uicomposer.components.LumeFieldState
import cl.lume.uicomposer.components.LumeList
import cl.lume.uicomposer.components.LumeListAccessory
import cl.lume.uicomposer.components.LumeListChrome
import cl.lume.uicomposer.components.LumeListDensity
import cl.lume.uicomposer.components.LumeListIconInk
import cl.lume.uicomposer.components.LumeListRow
import cl.lume.uicomposer.components.LumeListRowTextStyle
import cl.lume.uicomposer.components.LumeMenuPicker
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAxis
import cl.lume.uicomposer.components.LumeStackStyle
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTimeSlot
import cl.lume.uicomposer.components.LumeTimeSlotGrid
import cl.lume.uicomposer.components.LumeTimeSlotState
import cl.lume.uicomposer.foundations.LumeIcon
import cl.lume.uicomposer.tokens.LumeStatusStyle
import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.BookingFailure
import com.luismejias.lumemedlink.core.care.DirectoryDoctor
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.Slot
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.core.screen.CareLoadView
import com.luismejias.lumemedlink.core.screen.capitalized
import com.luismejias.lumemedlink.core.screen.longDateText
import com.luismejias.lumemedlink.core.screen.longWeekdayDayText
import com.luismejias.lumemedlink.core.screen.strongRuns
import com.luismejias.lumemedlink.features.patient.parts.DoctorIdentity
import com.luismejias.lumemedlink.features.patient.parts.PushedScreen
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
import com.luismejias.lumemedlink.resources.months_long
import com.luismejias.lumemedlink.resources.pending_with_body
import com.luismejias.lumemedlink.resources.pending_with_title
import com.luismejias.lumemedlink.resources.schedule_afternoon
import com.luismejias.lumemedlink.resources.schedule_cancel
import com.luismejias.lumemedlink.resources.schedule_clash
import com.luismejias.lumemedlink.resources.schedule_continue
import com.luismejias.lumemedlink.resources.schedule_month
import com.luismejias.lumemedlink.resources.schedule_morning
import com.luismejias.lumemedlink.resources.schedule_none
import com.luismejias.lumemedlink.resources.schedule_title
import com.luismejias.lumemedlink.resources.schedule_year
import com.luismejias.lumemedlink.resources.schedule_yours
import com.luismejias.lumemedlink.resources.schedule_yours_spoken
import com.luismejias.lumemedlink.resources.sent_done
import com.luismejias.lumemedlink.resources.sent_title
import com.luismejias.lumemedlink.resources.sent_view
import com.luismejias.lumemedlink.resources.sent_waiting
import com.luismejias.lumemedlink.resources.taken_action
import com.luismejias.lumemedlink.resources.taken_body
import com.luismejias.lumemedlink.resources.taken_title
import com.luismejias.lumemedlink.resources.weekdays_short
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** The doctor at the head of a1 and a2. */
@Composable
private fun DoctorHeader(entry: DirectoryDoctor) {
    LumeCard(modifier = Modifier.fillMaxWidth(), style = LumeCardStyle.Filled) {
        DoctorIdentity(entry.doctor, listOf(entry.doctor.specialty)) { DoctorRating(entry) }
    }
}

/**
 * a1. The month and year menus offer only months with open agenda; the strip draws every day of the month in the
 * booking window and refuses the ones without hours. An hour the patient already holds is drawn as «Tu cita» and
 * refused, with a line saying why. PROVISIONAL (ADR-0046): the taken hours are not drawn — availability returns only
 * free ones.
 */
@Composable
internal fun ScheduleScreen(model: SearchViewModel, onBack: () -> Unit) {
    val load by model.schedule.state.collectAsState()
    val standIn by model.schedule.standIn.collectAsState()
    val ready = (load as? CareLoad.Ready)?.content
    PushedScreen(
        title = stringResource(Res.string.schedule_title),
        onBack = onBack,
        action = {
            LumeStack(axis = LumeStackAxis.Horizontal, style = LumeStackStyle.Paired) {
                LumeButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.schedule_cancel)),
                    emphasis = LumeButtonEmphasis.OutlineNeutral,
                    size = LumeButtonSize.Bar,
                    width = LumeButtonWidth.Fill,
                    onClick = onBack,
                )
                LumeButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.schedule_continue)),
                    size = LumeButtonSize.Bar,
                    width = LumeButtonWidth.Fill,
                    state = if (ready?.slot != null) LumeFieldState.Enabled else LumeFieldState.Disabled,
                    onClick = { ready?.slot?.let { model.confirm(ready.doctor, it) } },
                )
            }
        },
    ) {
        CareLoadView(load, standIn, onRetry = {
            ready?.doctor?.let { doctor -> model.launch { schedule.load(doctor) } }
        }) { content ->
            DoctorHeader(content.doctor)
            val day = content.day
            if (day == null) {
                LumeEmptyState(icon = LumeIcon.Calendar, title = stringResource(Res.string.schedule_none))
                return@CareLoadView
            }
            MonthAndYear(content, model.schedule)
            DayStrip(content, model.schedule)
            if (content.morning.isNotEmpty()) {
                LumeEyebrow(stringResource(Res.string.schedule_morning, longWeekdayDayText(day)))
                HourGrid(content.morning, content.slot, model.schedule::slotChosen)
            }
            Clashes(content.morningClashes)
            if (content.afternoon.isNotEmpty()) {
                LumeEyebrow(stringResource(Res.string.schedule_afternoon))
                HourGrid(content.afternoon, content.slot, model.schedule::slotChosen)
            }
            Clashes(content.afternoonClashes)
        }
    }
}

@Composable
private fun MonthAndYear(content: ScheduleContent, schedule: ScheduleModel) {
    val monthNames = stringArrayResource(Res.array.months_long)
    LumeStack(axis = LumeStackAxis.Horizontal, spacing = { small }) {
        LumeMenuPicker(
            options = content.months,
            selected = content.month,
            label = { monthNames[it.month - 1].capitalized() },
            onSelect = schedule::monthChosen,
            contentDescription = stringResource(Res.string.schedule_month),
        )
        LumeMenuPicker(
            options = content.years,
            selected = content.month.year,
            label = { it.toString() },
            onSelect = schedule::yearChosen,
            contentDescription = stringResource(Res.string.schedule_year),
        )
    }
}

@Composable
private fun DayStrip(content: ScheduleContent, schedule: ScheduleModel) {
    val weekdays = stringArrayResource(Res.array.weekdays_short)
    val strip = content.strip
    val open = content.days.toSet()
    val spoken = strip.associateWith { longDateText(it) }
    LumeDayStrip(
        days = strip.map { date ->
            LumeDay(
                weekday = weekdays[date.isoWeekday - 1].capitalized(),
                day = date.day.toString(),
                contentDescription = spoken.getValue(date),
                state = if (date in open) LumeDayState.Available else LumeDayState.Unavailable,
            )
        },
        selected = strip.indexOf(content.day).takeIf { it >= 0 },
        onSelect = { schedule.dayChosen(strip[it]) },
    )
}

/** Half a day of hours: the free ones to choose and the patient's own, refused, in time order. */
@Composable
private fun HourGrid(tiles: List<HourTile>, chosen: Slot?, onChoose: (Slot) -> Unit) {
    val yours = stringResource(Res.string.schedule_yours)
    val spokenYours = tiles.filterIsInstance<HourTile.Mine>().associateWith {
        stringResource(Res.string.schedule_yours_spoken, it.start.display)
    }
    LumeTimeSlotGrid(
        slots = tiles.map { tile ->
            when (tile) {
                is HourTile.Free -> LumeTimeSlot(tile.start.display)
                is HourTile.Mine -> LumeTimeSlot(
                    time = tile.start.display,
                    state = LumeTimeSlotState.Booked,
                    note = yours,
                    contentDescription = spokenYours.getValue(tile),
                )
            }
        },
        selected = tiles.indexOfFirst { it is HourTile.Free && it.slot == chosen }.takeIf { it >= 0 },
        onSelect = { index -> (tiles[index] as? HourTile.Free)?.let { onChoose(it.slot) } },
    )
}

/** «A las 09:00 ya tienes cita con el Dr. Pinto», under the half of the day the hour belongs to. */
@Composable
private fun Clashes(clashes: List<PatientAppointment>) {
    clashes.forEach { clash ->
        LumeText(
            text = stringResource(Res.string.schedule_clash, clash.start.display, clash.doctor.displayName),
            color = LumeTextColor.Warning,
        )
    }
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
            LumeStack(axis = LumeStackAxis.Horizontal, style = LumeStackStyle.Paired) {
                LumeButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.confirm_back)),
                    emphasis = LumeButtonEmphasis.OutlineNeutral,
                    size = LumeButtonSize.Bar,
                    width = LumeButtonWidth.Fill,
                    onClick = onBack,
                )
                LumeAsyncButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.confirm_action)),
                    size = LumeButtonSize.Bar,
                    width = LumeButtonWidth.Fill,
                    feedback = LumeButtonFeedback(loading = stringResource(Res.string.confirm_running)),
                    run = model.booking::book,
                )
            }
        },
    ) {
        DoctorHeader(doctor)
        LumeList(
            items = listOf(0, 1, 2),
            key = { it },
            density = LumeListDensity.Compact,
            scrolls = false,
            chrome = LumeListChrome.Open,
        ) { row ->
            when (row) {
                0 -> DetailRow(
                    LumeIcon.Calendar,
                    stringResource(Res.string.confirm_when),
                    stringResource(Res.string.confirm_when_value, date, slot.start.display),
                )
                1 -> DetailRow(
                    LumeIcon.Clock,
                    stringResource(Res.string.confirm_duration),
                    stringResource(Res.string.confirm_duration_value, slot.minutes),
                )
                else -> DetailRow(
                    LumeIcon.Pin,
                    stringResource(Res.string.confirm_place),
                    "${doctor.place.address}\n${doctor.place.detail}",
                )
            }
        }
        LumeCallout(
            runs = stringResource(Res.string.confirm_note, doctor.doctor.displayName).strongRuns(),
            style = LumeCalloutStyle.Info,
            surface = LumeCalloutSurface.Washed,
        )
    }
    when (val outcome = state.outcome) {
        null -> Unit
        is BookingOutcome.Sent -> {
            val status = outcome.appointment.status
            val waiting = status == AppointmentStatus.REQUESTED
            val waitingNote = stringResource(Res.string.sent_waiting).strongRuns()
            LumeAlert(
                visible = true,
                style = LumeStatusStyle.Success,
                title = stringResource(if (waiting) Res.string.sent_title else Res.string.booked_title),
                message = stringResource(Res.string.booked_body, date, slot.start.display, doctor.doctor.displayName),
                confirm = LumeAlertAction(stringResource(Res.string.sent_view)) { onSeeAppointments(status) },
                cancel = LumeAlertAction(stringResource(Res.string.sent_done)) { onDone(status) },
                presentation = LumeAlertPresentation.Result,
            ) {
                if (waiting) {
                    LumeCallout(
                        runs = waitingNote,
                        style = LumeCalloutStyle.Warning,
                        surface = LumeCalloutSurface.Washed,
                        icon = LumeIcon.Bell,
                    )
                }
            }
        }
        is BookingOutcome.Refused -> RefusalAlert(outcome.failure, slot, doctor, model)
    }
}

/** One line of a2's summary: the label small over the value, the glyph in the accent. */
@Composable
private fun DetailRow(icon: LumeIcon, label: String, value: String) {
    LumeListRow(
        title = label,
        description = value,
        leading = LumeListAccessory.Icon(icon, "", ink = LumeListIconInk.Accent),
        textStyle = LumeListRowTextStyle.LabelOverValue,
    )
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
