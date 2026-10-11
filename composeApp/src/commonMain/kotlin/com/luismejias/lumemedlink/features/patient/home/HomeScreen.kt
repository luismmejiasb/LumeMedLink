package com.luismejias.lumemedlink.features.patient.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cl.lume.uicomposer.components.LumeAvatar
import cl.lume.uicomposer.components.LumeButton
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeButtonEmphasis
import cl.lume.uicomposer.components.LumeButtonWidth
import cl.lume.uicomposer.components.LumeCard
import cl.lume.uicomposer.components.LumeCardStyle
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerScroll
import cl.lume.uicomposer.components.LumeEmptyState
import cl.lume.uicomposer.components.LumeEyebrow
import cl.lume.uicomposer.components.LumeIconTile
import cl.lume.uicomposer.components.LumeIconTileEmphasis
import cl.lume.uicomposer.components.LumeIconTileSize
import cl.lume.uicomposer.components.LumeList
import cl.lume.uicomposer.components.LumeListAccessory
import cl.lume.uicomposer.components.LumeListRow
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeStackAxis
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTextStyle
import cl.lume.uicomposer.foundations.LumeIcon
import cl.lume.uicomposer.tokens.LumeTileTint
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.RatingPrompt
import com.luismejias.lumemedlink.features.patient.parts.AppointmentStatusTag
import com.luismejias.lumemedlink.features.patient.parts.CareLoadView
import com.luismejias.lumemedlink.features.patient.parts.dayMonthText
import com.luismejias.lumemedlink.features.patient.parts.doctorMarker
import com.luismejias.lumemedlink.features.patient.parts.longDateText
import com.luismejias.lumemedlink.features.patient.parts.relativeDayText
import com.luismejias.lumemedlink.features.patient.parts.shortDateText
import com.luismejias.lumemedlink.features.patient.parts.weekdayDayText
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.app_name
import com.luismejias.lumemedlink.resources.appointment_minutes
import com.luismejias.lumemedlink.resources.home_change_or_cancel
import com.luismejias.lumemedlink.resources.home_directions
import com.luismejias.lumemedlink.resources.home_empty_body
import com.luismejias.lumemedlink.resources.home_empty_title
import com.luismejias.lumemedlink.resources.home_greeting
import com.luismejias.lumemedlink.resources.home_next
import com.luismejias.lumemedlink.resources.home_next_today
import com.luismejias.lumemedlink.resources.home_rate_title
import com.luismejias.lumemedlink.resources.home_waiting
import com.luismejias.lumemedlink.resources.search_doctor
import org.jetbrains.compose.resources.stringResource

/**
 * Inicio (p1, e7). PROVISIONAL deviations (ADR-0046): the bell and the profile disc of the design's header wait
 * for the kit (LumeNavBar has no trailing slot, on purpose), and the hero card is the kit's accent card — a
 * tinted wash — where the design fills it with the accent.
 */
@Composable
internal fun HomeScreen(
    model: HomeViewModel,
    onDirections: (PatientAppointment) -> Unit,
    onChangeOrCancel: (PatientAppointment) -> Unit,
    onOpenAppointment: (PatientAppointment) -> Unit,
    onRate: (RatingPrompt) -> Unit,
    onSearch: () -> Unit,
) {
    val load by model.state.collectAsState()
    CareLoadView(load, onRetry = model::refresh, standalone = true) { home ->
        LumeContainer(scroll = LumeContainerScroll.Scrollable) {
            LumeStack(alignment = LumeStackAlignment.Start, spacing = { large }) {
                HomeBrand()
                LumeStack(alignment = LumeStackAlignment.Start, spacing = { xSmall }) {
                    LumeText(text = longDateText(home.today), color = LumeTextColor.Secondary)
                    LumeText(
                        text = stringResource(Res.string.home_greeting, home.givenNames),
                        style = LumeTextStyle.HeadingLarge,
                    )
                }
                val next = home.next
                if (next != null) {
                    NextAppointmentCard(next, home, onDirections, onChangeOrCancel)
                } else if (home.waiting.isEmpty()) {
                    LumeCard(modifier = Modifier.fillMaxWidth(), style = LumeCardStyle.Absent) {
                        LumeEmptyState(
                            icon = LumeIcon.Calendar,
                            title = stringResource(Res.string.home_empty_title),
                            message = stringResource(Res.string.home_empty_body),
                            action = LumeButtonContent.Title(stringResource(Res.string.search_doctor)),
                            onAction = onSearch,
                        )
                    }
                }
                if (home.waiting.isNotEmpty()) {
                    LumeList(
                        items = home.waiting,
                        key = { it.id },
                        scrolls = false,
                        onRowTap = onOpenAppointment,
                    ) { waiting ->
                        LumeListRow(
                            title = stringResource(Res.string.home_waiting),
                            description = listOf(
                                waiting.doctor.displayName,
                                waiting.doctor.specialty,
                                weekdayDayText(waiting.date),
                                waiting.start.display,
                            ).joinToString(" · "),
                            leading = LumeListAccessory.Tile(LumeIcon.Clock, LumeTileTint.Amber),
                            disclosure = true,
                        )
                    }
                }
                home.rating?.let { rating ->
                    LumeList(items = listOf(rating), key = { it.appointmentId }, scrolls = false, onRowTap = onRate) {
                        LumeListRow(
                            title = stringResource(Res.string.home_rate_title),
                            description = "${it.doctor.displayName} · ${dayMonthText(it.date)}",
                            leading = doctorMarker(it.doctor),
                            trailing = LumeListAccessory.Icon(LumeIcon.Star, ""),
                            disclosure = true,
                        )
                    }
                }
            }
        }
    }
}

/** PROVISIONAL (ADR-0044, deviation 6): the kit's shield and the name, until the wordmark's SVG. */
@Composable
private fun HomeBrand() {
    LumeStack(axis = LumeStackAxis.Horizontal, alignment = LumeStackAlignment.Center, spacing = { small }) {
        LumeIconTile(icon = LumeIcon.Shield, emphasis = LumeIconTileEmphasis.Solid, size = LumeIconTileSize.Card)
        LumeText(text = stringResource(Res.string.app_name), style = LumeTextStyle.HeadingSmall)
    }
}

@Composable
private fun NextAppointmentCard(
    next: PatientAppointment,
    home: HomeContent,
    onDirections: (PatientAppointment) -> Unit,
    onChangeOrCancel: (PatientAppointment) -> Unit,
) {
    val relative = relativeDayText(next.date, home.today)
    LumeCard(modifier = Modifier.fillMaxWidth(), style = LumeCardStyle.Accent) {
        LumeStack(alignment = LumeStackAlignment.Start, spacing = { medium }) {
            LumeStack(alignment = LumeStackAlignment.Start, spacing = { xSmall }) {
                LumeEyebrow(
                    title = stringResource(
                        if (home.today == next.date) Res.string.home_next_today else Res.string.home_next,
                    ),
                )
                AppointmentStatusTag(next.status)
            }
            LumeStack(axis = LumeStackAxis.Horizontal, alignment = LumeStackAlignment.Center, spacing = { medium }) {
                LumeAvatar(
                    initials = next.doctor.initials,
                    tint = LumeTileTint.deterministic(next.doctor.id),
                    contentDescription = next.doctor.displayName,
                )
                LumeStack(alignment = LumeStackAlignment.Start, spacing = { xSmall }) {
                    LumeText(text = next.doctor.displayName, style = LumeTextStyle.HeadingSmall)
                    LumeText(text = next.doctor.specialty, color = LumeTextColor.Secondary)
                }
            }
            LumeCard(modifier = Modifier.fillMaxWidth(), style = LumeCardStyle.Filled) {
                LumeListRow(
                    title = listOf(
                        relative ?: shortDateText(next.date),
                        next.start.display,
                        stringResource(Res.string.appointment_minutes, next.minutes),
                    ).joinToString(" · "),
                    leading = LumeListAccessory.Icon(LumeIcon.Clock, ""),
                    modifier = Modifier.fillMaxWidth(),
                )
                LumeListRow(
                    title = next.place.address,
                    description = next.place.detail,
                    leading = LumeListAccessory.Icon(LumeIcon.Pin, ""),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LumeStack(modifier = Modifier.fillMaxWidth(), alignment = LumeStackAlignment.Fill, spacing = { small }) {
                LumeButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.home_directions)),
                    width = LumeButtonWidth.Fill,
                    onClick = { onDirections(next) },
                )
                LumeButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.home_change_or_cancel)),
                    emphasis = LumeButtonEmphasis.OutlineNeutral,
                    width = LumeButtonWidth.Fill,
                    onClick = { onChangeOrCancel(next) },
                )
            }
        }
    }
}
