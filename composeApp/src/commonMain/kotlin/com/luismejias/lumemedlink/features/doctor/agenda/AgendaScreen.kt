package com.luismejias.lumemedlink.features.doctor.agenda

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cl.lume.uicomposer.components.LumeAlert
import cl.lume.uicomposer.components.LumeAlertAction
import cl.lume.uicomposer.components.LumeAsyncButton
import cl.lume.uicomposer.components.LumeAvatar
import cl.lume.uicomposer.components.LumeAvatarSize
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeButtonEmphasis
import cl.lume.uicomposer.components.LumeButtonSize
import cl.lume.uicomposer.components.LumeButtonWidth
import cl.lume.uicomposer.components.LumeCard
import cl.lume.uicomposer.components.LumeCardStyle
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerScroll
import cl.lume.uicomposer.components.LumeDay
import cl.lume.uicomposer.components.LumeDayStrip
import cl.lume.uicomposer.components.LumeDayStripLayout
import cl.lume.uicomposer.components.LumeList
import cl.lume.uicomposer.components.LumeListAccessory
import cl.lume.uicomposer.components.LumeListRow
import cl.lume.uicomposer.components.LumeListRowTextStyle
import cl.lume.uicomposer.components.LumeListStyle
import cl.lume.uicomposer.components.LumeNavBar
import cl.lume.uicomposer.components.LumeNavBarAction
import cl.lume.uicomposer.components.LumeNavBarTitleStyle
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeStackAxis
import cl.lume.uicomposer.components.LumeStackStyle
import cl.lume.uicomposer.components.LumeTag
import cl.lume.uicomposer.components.LumeTagStyle
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTextStyle
import cl.lume.uicomposer.components.LumeTextWeight
import cl.lume.uicomposer.tokens.LumeStatusStyle
import cl.lume.uicomposer.tokens.LumeTileTint
import com.luismejias.lumemedlink.core.care.AgendaEntry
import com.luismejias.lumemedlink.core.care.CareDoctor
import com.luismejias.lumemedlink.core.care.ChangeFailure
import com.luismejias.lumemedlink.core.care.VisitKind
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.core.screen.CareLoadView
import com.luismejias.lumemedlink.core.screen.TabBarClearance
import com.luismejias.lumemedlink.core.screen.capitalized
import com.luismejias.lumemedlink.core.screen.longDateText
import com.luismejias.lumemedlink.core.screen.longWeekdayDayText
import com.luismejias.lumemedlink.core.screen.statusLabel
import com.luismejias.lumemedlink.core.screen.weekdayDayText
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.common_close
import com.luismejias.lumemedlink.resources.doctor_agenda_note
import com.luismejias.lumemedlink.resources.doctor_answer_failed
import com.luismejias.lumemedlink.resources.doctor_answer_offline
import com.luismejias.lumemedlink.resources.doctor_answer_unavailable
import com.luismejias.lumemedlink.resources.doctor_confirm
import com.luismejias.lumemedlink.resources.doctor_day_empty
import com.luismejias.lumemedlink.resources.doctor_day_entries
import com.luismejias.lumemedlink.resources.doctor_profile_action
import com.luismejias.lumemedlink.resources.doctor_reject
import com.luismejias.lumemedlink.resources.doctor_request_line
import com.luismejias.lumemedlink.resources.doctor_requests
import com.luismejias.lumemedlink.resources.doctor_tab_agenda
import com.luismejias.lumemedlink.resources.doctor_today
import com.luismejias.lumemedlink.resources.visit_first
import com.luismejias.lumemedlink.resources.visit_follow_up
import com.luismejias.lumemedlink.resources.visit_teleconsult
import com.luismejias.lumemedlink.resources.weekdays_initial
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** The closed list's words (the author's call of 2026-10-11): never free text, never what the visit is about. */
@Composable
internal fun visitKindText(kind: VisitKind): String = stringResource(
    when (kind) {
        VisitKind.FIRST_VISIT -> Res.string.visit_first
        VisitKind.FOLLOW_UP -> Res.string.visit_follow_up
        VisitKind.TELECONSULT -> Res.string.visit_teleconsult
    },
)

/**
 * m1 · Agenda: the week, the requests waiting for an answer, and the chosen day. A simple view — the schedule,
 * overbooking and statistics stay in LumeMed. Never a clinical reason: the visit kind is a closed list.
 */
@Composable
internal fun AgendaScreen(model: AgendaViewModel, doctor: CareDoctor?, onProfile: () -> Unit) {
    val load by model.state.collectAsState()
    val failure by model.failure.collectAsState()
    val today = (load as? CareLoad.Ready)?.content?.today
    val profileLabel = stringResource(Res.string.doctor_profile_action)
    LumeContainer(
        scroll = LumeContainerScroll.Scrollable,
        topBar = {
            LumeNavBar(
                title = stringResource(Res.string.doctor_tab_agenda),
                titleStyle = LumeNavBarTitleStyle.Root,
                overline = today?.let { longDateText(it) },
                actions = listOfNotNull(
                    doctor?.let {
                        LumeNavBarAction.Initials(
                            it.initials,
                            profileLabel,
                            LumeTileTint.deterministic(it.id),
                            onClick = onProfile,
                        )
                    },
                ),
            )
        },
    ) {
        LumeStack(alignment = LumeStackAlignment.Start, spacing = { medium }) {
            CareLoadView(load, onRetry = model::refresh) { content ->
                WeekStrip(content, model)
                content.requests.firstOrNull()?.let { RequestCard(it, content.requests.size, model) }
                DayCard(content)
                LumeText(
                    text = stringResource(Res.string.doctor_agenda_note),
                    style = LumeTextStyle.TextSmall,
                    color = LumeTextColor.Secondary,
                )
            }
            TabBarClearance()
        }
    }
    failure?.let {
        LumeAlert(
            visible = true,
            style = LumeStatusStyle.Warning,
            title = stringResource(Res.string.doctor_answer_failed),
            message = stringResource(
                if (it ==
                    ChangeFailure.Offline
                ) {
                    Res.string.doctor_answer_offline
                } else {
                    Res.string.doctor_answer_unavailable
                },
            ),
            confirm = LumeAlertAction(stringResource(Res.string.common_close)) { model.failureSeen() },
        )
    }
}

@Composable
private fun WeekStrip(content: AgendaContent, model: AgendaViewModel) {
    val initials = stringArrayResource(Res.array.weekdays_initial)
    LumeDayStrip(
        days = content.week.map { date ->
            LumeDay(
                weekday = initials[date.isoWeekday - 1],
                day = date.day.toString(),
                contentDescription = longWeekdayDayText(date),
            )
        },
        selected = content.week.indexOf(content.day).takeIf { it >= 0 },
        onSelect = { index -> model.launch { dayChosen(content.week[index]) } },
        layout = LumeDayStripLayout.Fill,
    )
}

/** «Solicitudes por confirmar»: the soonest request with its answer; the next one comes up once it is answered. */
@Composable
private fun RequestCard(request: AgendaEntry, count: Int, model: AgendaViewModel) {
    LumeCard(modifier = Modifier.fillMaxWidth(), style = LumeCardStyle.Filled) {
        LumeStack(alignment = LumeStackAlignment.Fill, spacing = { medium }) {
            LumeStack(axis = LumeStackAxis.Horizontal, style = LumeStackStyle.Spread) {
                LumeText(text = stringResource(Res.string.doctor_requests), weight = LumeTextWeight.Strong)
                LumeTag(text = count.toString(), style = LumeTagStyle.Warning)
            }
            LumeStack(axis = LumeStackAxis.Horizontal, alignment = LumeStackAlignment.Center) {
                LumeAvatar(
                    initials = request.patient.initials,
                    size = LumeAvatarSize.Small,
                    tint = LumeTileTint.deterministic(request.patient.id),
                    contentDescription = request.patient.displayName,
                )
                LumeStack(alignment = LumeStackAlignment.Start, spacing = { xSmall }) {
                    LumeText(text = request.patient.displayName, weight = LumeTextWeight.Strong)
                    LumeText(
                        text = stringResource(
                            Res.string.doctor_request_line,
                            weekdayDayText(request.date).capitalized(),
                            request.start.display,
                            visitKindText(request.kind).lowercase(),
                        ),
                        style = LumeTextStyle.TextSmall,
                        color = LumeTextColor.Secondary,
                    )
                }
            }
            LumeStack(
                axis = LumeStackAxis.Horizontal,
                style = LumeStackStyle.Fixed,
                alignment = LumeStackAlignment.Fill,
                spacing = { small },
            ) {
                LumeAsyncButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.doctor_confirm)),
                    size = LumeButtonSize.Pair,
                    width = LumeButtonWidth.Fill,
                    run = { model.confirm(request) },
                )
                LumeAsyncButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.doctor_reject)),
                    emphasis = LumeButtonEmphasis.OutlineNeutral,
                    size = LumeButtonSize.Pair,
                    width = LumeButtonWidth.Fill,
                    run = { model.reject(request) },
                )
            }
        }
    }
}

/** «Hoy · 7 citas»: the day's appointments, each at its hour. */
@Composable
private fun DayCard(content: AgendaContent) {
    val dayName = if (content.day == content.today) {
        stringResource(Res.string.doctor_today)
    } else {
        weekdayDayText(content.day).capitalized()
    }
    LumeCard(modifier = Modifier.fillMaxWidth(), style = LumeCardStyle.Filled) {
        LumeStack(alignment = LumeStackAlignment.Fill, spacing = { small }) {
            LumeText(
                text = pluralStringResource(
                    Res.plurals.doctor_day_entries,
                    content.entries.size,
                    content.entries.size,
                    dayName,
                ),
                weight = LumeTextWeight.Strong,
            )
            if (content.entries.isEmpty()) {
                LumeText(
                    text = stringResource(Res.string.doctor_day_empty),
                    style = LumeTextStyle.TextSmall,
                    color = LumeTextColor.Secondary,
                )
            } else {
                LumeList(
                    items = content.entries,
                    key = { it.id },
                    style = LumeListStyle.Embedded,
                    scrolls = false,
                ) { entry ->
                    val status = statusLabel(entry.status)
                    LumeListRow(
                        title = entry.patient.displayName,
                        description = visitKindText(entry.kind),
                        leading = LumeListAccessory.Time(
                            entry.start.display,
                            next = LumeListAccessory.Avatar(
                                entry.patient.initials,
                                LumeTileTint.deterministic(entry.patient.id),
                                size = LumeAvatarSize.ExtraSmall,
                            ),
                        ),
                        trailing = status?.let { (text, style) -> LumeListAccessory.Tag(text, style) }
                            ?: LumeListAccessory.None,
                        textStyle = LumeListRowTextStyle.Strong,
                    )
                }
            }
        }
    }
}
