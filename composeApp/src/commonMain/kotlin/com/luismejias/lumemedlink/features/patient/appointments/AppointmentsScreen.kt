package com.luismejias.lumemedlink.features.patient.appointments

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerScroll
import cl.lume.uicomposer.components.LumeEmptyState
import cl.lume.uicomposer.components.LumeEmptyStateLook
import cl.lume.uicomposer.components.LumeList
import cl.lume.uicomposer.components.LumeListAccessory
import cl.lume.uicomposer.components.LumeListRow
import cl.lume.uicomposer.components.LumeListRowTextStyle
import cl.lume.uicomposer.components.LumeListStyle
import cl.lume.uicomposer.components.LumeNavBar
import cl.lume.uicomposer.components.LumeNavBarAction
import cl.lume.uicomposer.components.LumeNavBarActionEmphasis
import cl.lume.uicomposer.components.LumeNavBarTitleStyle
import cl.lume.uicomposer.components.LumeSegmentedControl
import cl.lume.uicomposer.components.LumeSegmentedSelection
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeStackAxis
import cl.lume.uicomposer.components.LumeStackStyle
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.screen.CareLoadView
import com.luismejias.lumemedlink.core.screen.TabBarClearance
import com.luismejias.lumemedlink.core.screen.capitalized
import com.luismejias.lumemedlink.core.screen.relativeDayText
import com.luismejias.lumemedlink.core.screen.shortDateText
import com.luismejias.lumemedlink.core.screen.statusLabel
import com.luismejias.lumemedlink.core.screen.strongRuns
import com.luismejias.lumemedlink.features.patient.parts.doctorMarker
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.appointments_cancel
import com.luismejias.lumemedlink.resources.appointments_cancelled
import com.luismejias.lumemedlink.resources.appointments_change
import com.luismejias.lumemedlink.resources.appointments_empty_cancelled
import com.luismejias.lumemedlink.resources.appointments_empty_past
import com.luismejias.lumemedlink.resources.appointments_empty_upcoming
import com.luismejias.lumemedlink.resources.appointments_empty_upcoming_body
import com.luismejias.lumemedlink.resources.appointments_past
import com.luismejias.lumemedlink.resources.appointments_requested_note
import com.luismejias.lumemedlink.resources.appointments_title
import com.luismejias.lumemedlink.resources.appointments_upcoming_plain
import com.luismejias.lumemedlink.resources.appointments_window
import com.luismejias.lumemedlink.resources.schedule_title
import com.luismejias.lumemedlink.resources.search_doctor
import com.luismejias.lumemedlink.shared.CalendarDate
import org.jetbrains.compose.resources.stringResource

/**
 * Mis citas (p5, e8). PROVISIONAL (ADR-0046): the calendar disc of the design's header waits for the kit, like
 * Inicio's bell.
 */
@Composable
internal fun AppointmentsScreen(
    model: AppointmentsViewModel,
    onOpen: (PatientAppointment) -> Unit,
    onChange: (PatientAppointment) -> Unit,
    onCancel: (PatientAppointment) -> Unit,
    onSearch: () -> Unit,
) {
    val load by model.state.collectAsState()
    val standIn by model.standIn.collectAsState()
    val segment by model.segment.collectAsState()
    CareLoadView(load, standIn, onRetry = model::refresh, standalone = true) { content ->
        LumeContainer(
            scroll = LumeContainerScroll.Scrollable,
            topBar = {
                LumeNavBar(
                    title = stringResource(Res.string.appointments_title),
                    titleStyle = LumeNavBarTitleStyle.Root,
                    actions = listOf(
                        LumeNavBarAction.Glyph(
                            LumeIcon.Calendar,
                            stringResource(Res.string.schedule_title),
                            LumeNavBarActionEmphasis.Accent,
                            onClick = onSearch,
                        ),
                    ),
                )
            },
        ) {
            LumeStack(alignment = LumeStackAlignment.Start, spacing = { medium }) {
                // Three tabs leave no room for a count (e8 draws «Próximas» bare); the tab bar's badge carries it.
                val upcomingLabel = stringResource(Res.string.appointments_upcoming_plain)
                val pastLabel = stringResource(Res.string.appointments_past)
                val cancelledLabel = stringResource(Res.string.appointments_cancelled)
                LumeSegmentedControl(
                    items = AppointmentsSegment.entries,
                    selection = LumeSegmentedSelection.Exclusive(segment, model::segmentChosen),
                    label = {
                        when (it) {
                            AppointmentsSegment.UPCOMING -> upcomingLabel
                            AppointmentsSegment.PAST -> pastLabel
                            AppointmentsSegment.CANCELLED -> cancelledLabel
                        }
                    },
                )
                val shown = when (segment) {
                    AppointmentsSegment.UPCOMING -> content.upcoming
                    AppointmentsSegment.PAST -> content.past
                    AppointmentsSegment.CANCELLED -> content.cancelled
                }
                if (shown.isEmpty()) {
                    LumeEmptyState(
                        look = LumeEmptyStateLook.Tiled(),
                        icon = LumeIcon.Calendar,
                        title = stringResource(
                            when (segment) {
                                AppointmentsSegment.UPCOMING -> Res.string.appointments_empty_upcoming
                                AppointmentsSegment.PAST -> Res.string.appointments_empty_past
                                AppointmentsSegment.CANCELLED -> Res.string.appointments_empty_cancelled
                            },
                        ),
                        message = if (segment == AppointmentsSegment.UPCOMING) {
                            stringResource(Res.string.appointments_empty_upcoming_body)
                        } else {
                            null
                        },
                        action = if (segment == AppointmentsSegment.UPCOMING) {
                            LumeButtonContent.Title(stringResource(Res.string.search_doctor))
                        } else {
                            null
                        },
                        onAction = onSearch,
                    )
                }
                shown.forEach { appointment ->
                    AppointmentCard(
                        appointment = appointment,
                        today = content.today,
                        actionable = segment == AppointmentsSegment.UPCOMING,
                        onOpen = onOpen,
                        onChange = onChange,
                        onCancel = onCancel,
                    )
                }
                if (segment == AppointmentsSegment.UPCOMING && content.upcoming.isNotEmpty()) {
                    LumeCallout(
                        runs = stringResource(Res.string.appointments_window, content.changeWindowHours).strongRuns(),
                        style = LumeCalloutStyle.Info,
                        surface = LumeCalloutSurface.Washed,
                    )
                }
                TabBarClearance()
            }
        }
    }
}

@Composable
private fun AppointmentCard(
    appointment: PatientAppointment,
    today: CalendarDate,
    actionable: Boolean,
    onOpen: (PatientAppointment) -> Unit,
    onChange: (PatientAppointment) -> Unit,
    onCancel: (PatientAppointment) -> Unit,
) {
    // The whole card opens the detail (the author, 2026-10-13: «al tocar cualquier parte de la card»); its two buttons
    // keep their own touch.
    val open = remember(appointment, onOpen) { { onOpen(appointment) } }
    LumeCard(modifier = Modifier.fillMaxWidth(), style = LumeCardStyle.Filled, onClick = open) {
        LumeStack(alignment = LumeStackAlignment.Start, spacing = { medium }) {
            LumeList(
                items = listOf(appointment),
                key = { it.id },
                style = LumeListStyle.Embedded,
            ) {
                LumeListRow(
                    textStyle = LumeListRowTextStyle.Strong,
                    title = it.doctor.displayName,
                    description = it.doctor.specialty,
                    leading = doctorMarker(it.doctor),
                    trailing = statusAccessory(it.status),
                    disclosure = true,
                )
            }
            val relative = relativeDayText(appointment.date, today)
            val date = shortDateText(appointment.date)
            LumeCard(modifier = Modifier.fillMaxWidth(), style = LumeCardStyle.Filled) {
                LumeListRow(
                    textStyle = LumeListRowTextStyle.Strong,
                    title = listOfNotNull(
                        relative,
                        if (relative == null) date.capitalized() else date,
                        appointment.start.display,
                    ).joinToString(" · "),
                    leading = LumeListAccessory.Icon(LumeIcon.Calendar, ""),
                )
            }
            when {
                actionable && appointment.status == AppointmentStatus.REQUESTED -> LumeText(
                    text = stringResource(Res.string.appointments_requested_note),
                    color = LumeTextColor.Secondary,
                )
                actionable -> LumeStack(
                    axis = LumeStackAxis.Horizontal,
                    style = LumeStackStyle.Fixed,
                    alignment = LumeStackAlignment.Fill,
                    spacing = { small },
                ) {
                    LumeButton(
                        content = LumeButtonContent.Title(stringResource(Res.string.appointments_change)),
                        emphasis = LumeButtonEmphasis.OutlineNeutral,
                        size = LumeButtonSize.Pair,
                        width = LumeButtonWidth.Fill,
                        onClick = { onChange(appointment) },
                    )
                    LumeButton(
                        content = LumeButtonContent.Title(stringResource(Res.string.appointments_cancel)),
                        emphasis = LumeButtonEmphasis.Outline,
                        role = LumeButtonRole.Destructive,
                        size = LumeButtonSize.Pair,
                        width = LumeButtonWidth.Fill,
                        onClick = { onCancel(appointment) },
                    )
                }
            }
        }
    }
}

@Composable
private fun statusAccessory(status: AppointmentStatus): LumeListAccessory =
    statusLabel(status)?.let { (text, style) -> LumeListAccessory.Tag(text, style) } ?: LumeListAccessory.None
