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
import cl.lume.uicomposer.components.LumeButtonSize
import cl.lume.uicomposer.components.LumeButtonWidth
import cl.lume.uicomposer.components.LumeCard
import cl.lume.uicomposer.components.LumeCardInset
import cl.lume.uicomposer.components.LumeCardStyle
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerScroll
import cl.lume.uicomposer.components.LumeEmptyState
import cl.lume.uicomposer.components.LumeEmptyStateLook
import cl.lume.uicomposer.components.LumeEyebrow
import cl.lume.uicomposer.components.LumeList
import cl.lume.uicomposer.components.LumeListAccessory
import cl.lume.uicomposer.components.LumeListChrome
import cl.lume.uicomposer.components.LumeListRow
import cl.lume.uicomposer.components.LumeListRowTextStyle
import cl.lume.uicomposer.components.LumeNavBar
import cl.lume.uicomposer.components.LumeNavBarAction
import cl.lume.uicomposer.components.LumeRatingInput
import cl.lume.uicomposer.components.LumeRatingInputSize
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeStackAxis
import cl.lume.uicomposer.components.LumeStackStyle
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTextStyle
import cl.lume.uicomposer.components.LumeTextWeight
import cl.lume.uicomposer.foundations.LumeIcon
import cl.lume.uicomposer.tokens.LumeTileTint
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.RatingPrompt
import com.luismejias.lumemedlink.core.screen.AppointmentStatusTag
import com.luismejias.lumemedlink.core.screen.CareLoadView
import com.luismejias.lumemedlink.core.screen.TabBarClearance
import com.luismejias.lumemedlink.core.screen.dayMonthText
import com.luismejias.lumemedlink.core.screen.longDateText
import com.luismejias.lumemedlink.core.screen.relativeDayText
import com.luismejias.lumemedlink.core.screen.shortDateText
import com.luismejias.lumemedlink.core.screen.weekdayDayText
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
import com.luismejias.lumemedlink.resources.home_notices
import com.luismejias.lumemedlink.resources.home_notices_new
import com.luismejias.lumemedlink.resources.home_profile
import com.luismejias.lumemedlink.resources.home_rate_title
import com.luismejias.lumemedlink.resources.home_waiting
import com.luismejias.lumemedlink.resources.lumemedlink_icon
import com.luismejias.lumemedlink.resources.rate_label
import com.luismejias.lumemedlink.resources.rate_none
import com.luismejias.lumemedlink.resources.rate_value
import com.luismejias.lumemedlink.resources.search_doctor
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Inicio (p1, e7). The header is the kit's brand bar with the bell and the profile disc; the hero card is the kit's
 * solid accent card.
 */
@Composable
internal fun HomeScreen(
    model: HomeViewModel,
    onDirections: (PatientAppointment) -> Unit,
    onChangeOrCancel: (PatientAppointment) -> Unit,
    onOpenAppointment: (PatientAppointment) -> Unit,
    onRate: (RatingPrompt, Int) -> Unit,
    onSearch: () -> Unit,
    onNotices: () -> Unit,
    onProfile: () -> Unit,
) {
    val load by model.state.collectAsState()
    CareLoadView(load, onRetry = model::refresh, standalone = true) { home ->
        LumeContainer(
            scroll = LumeContainerScroll.Scrollable,
            topBar = { HomeBar(home, onNotices, onProfile) },
        ) {
            LumeStack(alignment = LumeStackAlignment.Start, spacing = { large }) {
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
                    LumeCard(modifier = Modifier.fillMaxWidth(), style = LumeCardStyle.Dashed) {
                        LumeEmptyState(
                            look = LumeEmptyStateLook.Tiled(),
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
                        chrome = LumeListChrome.Open,
                        items = home.waiting,
                        key = { it.id },
                        scrolls = false,
                        onRowTap = onOpenAppointment,
                    ) { waiting ->
                        LumeListRow(
                            textStyle = LumeListRowTextStyle.Strong,
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
                home.rating?.let { RatingCard(it, onRate) }
                TabBarClearance()
            }
        }
    }
}

/**
 * p1's header: the app's mark and name, then the bell and the patient's initials. PROVISIONAL (ADR-0044, deviation 6):
 * the name is the kit's text in one colour until the handoff's whole logotype arrives as artwork.
 */
@Composable
private fun HomeBar(home: HomeContent, onNotices: () -> Unit, onProfile: () -> Unit) {
    val name = stringResource(Res.string.app_name)
    LumeNavBar(
        brand = painterResource(Res.drawable.lumemedlink_icon),
        brandDescription = name,
        brandName = name,
        actions = listOf(
            LumeNavBarAction.Glyph(
                LumeIcon.Bell,
                stringResource(Res.string.home_notices),
                unread = if (home.unreadNotices) stringResource(Res.string.home_notices_new) else null,
                onClick = onNotices,
            ),
            LumeNavBarAction.Initials(home.initials, stringResource(Res.string.home_profile), onClick = onProfile),
        ),
    )
}

@Composable
private fun NextAppointmentCard(
    next: PatientAppointment,
    home: HomeContent,
    onDirections: (PatientAppointment) -> Unit,
    onChangeOrCancel: (PatientAppointment) -> Unit,
) {
    val relative = relativeDayText(next.date, home.today)
    LumeCard(modifier = Modifier.fillMaxWidth(), style = LumeCardStyle.Solid, inset = LumeCardInset.Feature) {
        LumeStack(alignment = LumeStackAlignment.Start, spacing = { medium }) {
            LumeStack(axis = LumeStackAxis.Horizontal, style = LumeStackStyle.Spread) {
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
                    LumeText(text = next.doctor.specialty, style = LumeTextStyle.TextSmall)
                }
            }
            LumeCard(modifier = Modifier.fillMaxWidth(), style = LumeCardStyle.Filled) {
                LumeListRow(
                    textStyle = LumeListRowTextStyle.Strong,
                    title = listOf(
                        relative ?: shortDateText(next.date),
                        next.start.display,
                        stringResource(Res.string.appointment_minutes, next.minutes),
                    ).joinToString(" · "),
                    leading = LumeListAccessory.Icon(LumeIcon.Clock, ""),
                    modifier = Modifier.fillMaxWidth(),
                )
                LumeListRow(
                    textStyle = LumeListRowTextStyle.Strong,
                    title = next.place.address,
                    description = next.place.detail,
                    leading = LumeListAccessory.Icon(LumeIcon.Pin, ""),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LumeStack(
                axis = LumeStackAxis.Horizontal,
                style = LumeStackStyle.Fixed,
                alignment = LumeStackAlignment.Fill,
                spacing = { small },
            ) {
                LumeButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.home_directions)),
                    size = LumeButtonSize.Pair,
                    width = LumeButtonWidth.Fill,
                    onClick = { onDirections(next) },
                )
                LumeButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.home_change_or_cancel)),
                    emphasis = LumeButtonEmphasis.Outline,
                    size = LumeButtonSize.Pair,
                    width = LumeButtonWidth.Fill,
                    onClick = { onChangeOrCancel(next) },
                )
            }
        }
    }
}

/** «¿Cómo fue tu atención?»: the doctor, the visit, and the stars as a shortcut into rating it (f5). */
@Composable
private fun RatingCard(rating: RatingPrompt, onRate: (RatingPrompt, Int) -> Unit) {
    LumeCard(modifier = Modifier.fillMaxWidth(), style = LumeCardStyle.Filled) {
        LumeStack(axis = LumeStackAxis.Horizontal, alignment = LumeStackAlignment.Center) {
            LumeAvatar(
                initials = rating.doctor.initials,
                tint = LumeTileTint.deterministic(rating.doctor.id),
                contentDescription = rating.doctor.displayName,
            )
            LumeStack(alignment = LumeStackAlignment.Start, spacing = { xSmall }) {
                LumeText(text = stringResource(Res.string.home_rate_title), weight = LumeTextWeight.Strong)
                LumeText(
                    text = "${rating.doctor.displayName} · ${dayMonthText(rating.date)}",
                    style = LumeTextStyle.TextSmall,
                    color = LumeTextColor.Secondary,
                )
                val spoken = listOf(stringResource(Res.string.rate_none)) +
                    (1..MAX_STARS).map { stringResource(Res.string.rate_value, it) }
                LumeRatingInput(
                    value = 0,
                    onValueChange = { onRate(rating, it) },
                    contentDescription = stringResource(Res.string.rate_label),
                    valueDescription = { spoken.getOrElse(it) { spoken.first() } },
                    size = LumeRatingInputSize.Compact,
                )
            }
        }
    }
}

private const val MAX_STARS = 5
