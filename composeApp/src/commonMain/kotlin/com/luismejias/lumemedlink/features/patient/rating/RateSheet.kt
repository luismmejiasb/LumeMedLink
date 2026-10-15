package com.luismejias.lumemedlink.features.patient.rating

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.lume.uicomposer.components.LumeAsyncButton
import cl.lume.uicomposer.components.LumeAvatar
import cl.lume.uicomposer.components.LumeAvatarSize
import cl.lume.uicomposer.components.LumeButton
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeButtonEmphasis
import cl.lume.uicomposer.components.LumeButtonSize
import cl.lume.uicomposer.components.LumeButtonWidth
import cl.lume.uicomposer.components.LumeCallout
import cl.lume.uicomposer.components.LumeCalloutAction
import cl.lume.uicomposer.components.LumeCalloutStyle
import cl.lume.uicomposer.components.LumeCalloutSurface
import cl.lume.uicomposer.components.LumeEyebrow
import cl.lume.uicomposer.components.LumeFilterChip
import cl.lume.uicomposer.components.LumeFilterChipLook
import cl.lume.uicomposer.components.LumeRatingInput
import cl.lume.uicomposer.components.LumeSheet
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeStackAxis
import cl.lume.uicomposer.components.LumeStackStyle
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextAlignment
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTextStyle
import cl.lume.uicomposer.components.LumeTextWeight
import cl.lume.uicomposer.foundations.LumeIcon
import cl.lume.uicomposer.tokens.LumeTileTint
import com.luismejias.lumemedlink.core.care.RatingAspect
import com.luismejias.lumemedlink.core.screen.dayMonthText
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.aspect_clarity
import com.luismejias.lumemedlink.resources.aspect_explained_clearly
import com.luismejias.lumemedlink.resources.aspect_facilities
import com.luismejias.lumemedlink.resources.aspect_kind
import com.luismejias.lumemedlink.resources.aspect_long_wait
import com.luismejias.lumemedlink.resources.aspect_moved_hour
import com.luismejias.lumemedlink.resources.aspect_not_explained
import com.luismejias.lumemedlink.resources.aspect_notice
import com.luismejias.lumemedlink.resources.aspect_punctuality
import com.luismejias.lumemedlink.resources.aspect_solved_it
import com.luismejias.lumemedlink.resources.aspect_unexpected_charge
import com.luismejias.lumemedlink.resources.aspect_unkind
import com.luismejias.lumemedlink.resources.aspect_visit_time
import com.luismejias.lumemedlink.resources.rate_ask_bad
import com.luismejias.lumemedlink.resources.rate_ask_fair
import com.luismejias.lumemedlink.resources.rate_ask_good
import com.luismejias.lumemedlink.resources.rate_complaint
import com.luismejias.lumemedlink.resources.rate_complaint_action
import com.luismejias.lumemedlink.resources.rate_consent
import com.luismejias.lumemedlink.resources.rate_failed
import com.luismejias.lumemedlink.resources.rate_label
import com.luismejias.lumemedlink.resources.rate_later
import com.luismejias.lumemedlink.resources.rate_level_1
import com.luismejias.lumemedlink.resources.rate_level_2
import com.luismejias.lumemedlink.resources.rate_level_3
import com.luismejias.lumemedlink.resources.rate_level_4
import com.luismejias.lumemedlink.resources.rate_level_5
import com.luismejias.lumemedlink.resources.rate_none
import com.luismejias.lumemedlink.resources.rate_send
import com.luismejias.lumemedlink.resources.rate_subtitle
import com.luismejias.lumemedlink.resources.rate_title
import com.luismejias.lumemedlink.resources.rate_title_her
import com.luismejias.lumemedlink.resources.rate_title_him
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val ASPECT_TITLES: Map<RatingAspect, StringResource> = mapOf(
    RatingAspect.LONG_WAIT to Res.string.aspect_long_wait,
    RatingAspect.NOT_EXPLAINED to Res.string.aspect_not_explained,
    RatingAspect.UNKIND to Res.string.aspect_unkind,
    RatingAspect.UNEXPECTED_CHARGE to Res.string.aspect_unexpected_charge,
    RatingAspect.MOVED_HOUR to Res.string.aspect_moved_hour,
    RatingAspect.PUNCTUALITY to Res.string.aspect_punctuality,
    RatingAspect.CLARITY to Res.string.aspect_clarity,
    RatingAspect.VISIT_TIME to Res.string.aspect_visit_time,
    RatingAspect.NOTICE to Res.string.aspect_notice,
    RatingAspect.FACILITIES to Res.string.aspect_facilities,
    RatingAspect.EXPLAINED_CLEARLY to Res.string.aspect_explained_clearly,
    RatingAspect.KIND to Res.string.aspect_kind,
    RatingAspect.SOLVED_IT to Res.string.aspect_solved_it,
)

private val LEVEL_WORDS = listOf(
    Res.string.rate_level_1,
    Res.string.rate_level_2,
    Res.string.rate_level_3,
    Res.string.rate_level_4,
    Res.string.rate_level_5,
)

/**
 * f5a–f5d · Valorar la atención: the doctor, the stars at the level tapped, the aspects that level asks about, the
 * consent the rating is sent with, and — when it went badly — the road to a formal complaint. No free text: the
 * contract takes none, so the design's «Otro» and «Cuéntanos más» are gone (2026-10-15). «Hacer un reclamo» opens
 * nothing yet.
 */
@Composable
internal fun RateSheet(model: RateViewModel, onComplaint: () -> Unit) {
    val state by model.state.collectAsState()
    val shown = state
    val doctor = shown?.prompt?.doctor
    LumeSheet(
        visible = shown != null,
        onDismiss = model::dismiss,
        title = doctor?.let { stringResource(titleFor(it.displayName.substringBefore(" ")), it.displayName) }.orEmpty(),
        subtitle = shown?.let {
            stringResource(Res.string.rate_subtitle, dayMonthText(it.prompt.date), it.prompt.doctor.specialty)
        },
        art = doctor?.let {
            {
                LumeAvatar(
                    initials = it.initials,
                    size = LumeAvatarSize.Large,
                    tint = LumeTileTint.deterministic(it.id),
                    contentDescription = "",
                )
            }
        },
        actions = { RateActions(model) },
    ) {
        if (shown == null) return@LumeSheet
        val level = shown.level
        val spoken = listOf(stringResource(Res.string.rate_none)) + LEVEL_WORDS.map { stringResource(it) }
        LumeStack(modifier = Modifier.fillMaxWidth(), alignment = LumeStackAlignment.Center, spacing = { xSmall }) {
            LumeRatingInput(
                value = shown.stars,
                onValueChange = model::starsChanged,
                contentDescription = stringResource(Res.string.rate_label),
                valueDescription = { spoken.getOrElse(it) { spoken.first() } },
            )
            LumeText(
                text = spoken[shown.stars],
                color = levelInk(level),
                weight = LumeTextWeight.Strong,
                alignment = LumeTextAlignment.Center,
            )
        }
        LumeStack(alignment = LumeStackAlignment.Start, spacing = { small }) {
            LumeEyebrow(
                stringResource(
                    when (level) {
                        RatingLevel.BAD -> Res.string.rate_ask_bad
                        RatingLevel.FAIR -> Res.string.rate_ask_fair
                        RatingLevel.GOOD -> Res.string.rate_ask_good
                    },
                ),
            )
            AspectChips(level, shown, model)
        }
        // What sending means, before the button: the platform keeps this version with the rating as its record.
        LumeText(
            text = stringResource(Res.string.rate_consent),
            style = LumeTextStyle.TextSmall,
            color = LumeTextColor.Secondary,
        )
        // Inside the sheet: it draws over the screen, so a toast under it would never be seen.
        if (shown.failure != null) {
            LumeCallout(
                text = stringResource(Res.string.rate_failed),
                style = LumeCalloutStyle.Error,
                surface = LumeCalloutSurface.Washed,
                icon = LumeIcon.Alert,
            )
        }
        if (level == RatingLevel.BAD) {
            LumeCallout(
                text = stringResource(Res.string.rate_complaint),
                action = LumeCalloutAction(stringResource(Res.string.rate_complaint_action), onComplaint),
                style = LumeCalloutStyle.Error,
            )
        }
    }
}

/**
 * The level's aspects, wrapping as the design lays them (f5). No air of their own: each chip's 48 touch
 * target already keeps the rows ~12 apart, the closest the kit allows to the design's ~10.
 */
@Composable
private fun AspectChips(level: RatingLevel, state: RateState, model: RateViewModel) {
    LumeStack(axis = LumeStackAxis.Horizontal, style = LumeStackStyle.Wrap, spacing = { 0.dp }) {
        level.aspects.forEach { aspect ->
            val title = stringResource(ASPECT_TITLES.getValue(aspect))
            LumeFilterChip(
                title = title,
                isSelected = aspect in state.aspects,
                contentDescription = title,
                look = LumeFilterChipLook.Washed,
                onClick = { model.aspectToggled(aspect) },
            )
        }
    }
}

@Composable
private fun RateActions(model: RateViewModel) {
    LumeStack(axis = LumeStackAxis.Horizontal, style = LumeStackStyle.Paired) {
        LumeButton(
            content = LumeButtonContent.Title(stringResource(Res.string.rate_later)),
            emphasis = LumeButtonEmphasis.OutlineNeutral,
            size = LumeButtonSize.Bar,
            width = LumeButtonWidth.Fill,
            onClick = model::dismiss,
        )
        LumeAsyncButton(
            content = LumeButtonContent.Title(stringResource(Res.string.rate_send)),
            size = LumeButtonSize.Bar,
            width = LumeButtonWidth.Fill,
            run = model::send,
        )
    }
}

/**
 * «con el Dr.», «con la Dra.»: Spanish puts the article before the server's title, and only these two say which one.
 * Any other title reads without it.
 */
/** The article «el»/«la» from the name's first word, «Dr.» or «Dra.»; neither when it is not a title. */
private fun titleFor(title: String): StringResource = when (title.trim().trimEnd('.').lowercase()) {
    "dr" -> Res.string.rate_title_him
    "dra" -> Res.string.rate_title_her
    else -> Res.string.rate_title
}

/** The scale's colours: bad in the error ink, fair quiet, good in the stars' gold (spec f5). */
private fun levelInk(level: RatingLevel): LumeTextColor = when (level) {
    RatingLevel.BAD -> LumeTextColor.Error
    RatingLevel.FAIR -> LumeTextColor.Secondary
    RatingLevel.GOOD -> LumeTextColor.Warning
}
