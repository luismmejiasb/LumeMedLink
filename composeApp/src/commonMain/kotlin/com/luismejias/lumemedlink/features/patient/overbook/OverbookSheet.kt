package com.luismejias.lumemedlink.features.patient.overbook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import cl.lume.uicomposer.components.LumeAsyncButton
import cl.lume.uicomposer.components.LumeAvatarSize
import cl.lume.uicomposer.components.LumeButton
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeButtonEmphasis
import cl.lume.uicomposer.components.LumeButtonSize
import cl.lume.uicomposer.components.LumeButtonWidth
import cl.lume.uicomposer.components.LumeCallout
import cl.lume.uicomposer.components.LumeCalloutStyle
import cl.lume.uicomposer.components.LumeCalloutSurface
import cl.lume.uicomposer.components.LumeEyebrow
import cl.lume.uicomposer.components.LumeFieldState
import cl.lume.uicomposer.components.LumeFilterChip
import cl.lume.uicomposer.components.LumeFilterChipLook
import cl.lume.uicomposer.components.LumeSheet
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeStackAxis
import cl.lume.uicomposer.components.LumeStackStyle
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTextStyle
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.core.care.OverbookFailure
import com.luismejias.lumemedlink.core.care.OverbookReason
import com.luismejias.lumemedlink.core.care.OverbookUrgency
import com.luismejias.lumemedlink.features.patient.parts.DoctorAvatar
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.overbook_emergency
import com.luismejias.lumemedlink.resources.overbook_failed_not_taken
import com.luismejias.lumemedlink.resources.overbook_failed_offline
import com.luismejias.lumemedlink.resources.overbook_failed_unavailable
import com.luismejias.lumemedlink.resources.overbook_failed_waiting
import com.luismejias.lumemedlink.resources.overbook_how_answered
import com.luismejias.lumemedlink.resources.overbook_later
import com.luismejias.lumemedlink.resources.overbook_reason_earlier_follow_up
import com.luismejias.lumemedlink.resources.overbook_reason_heading
import com.luismejias.lumemedlink.resources.overbook_reason_other
import com.luismejias.lumemedlink.resources.overbook_reason_renew_prescription
import com.luismejias.lumemedlink.resources.overbook_reason_review_results
import com.luismejias.lumemedlink.resources.overbook_send
import com.luismejias.lumemedlink.resources.overbook_sheet_title
import com.luismejias.lumemedlink.resources.overbook_urgency_heading
import com.luismejias.lumemedlink.resources.overbook_urgency_normal
import com.luismejias.lumemedlink.resources.overbook_urgency_priority
import com.luismejias.lumemedlink.resources.overbook_urgency_urgent
import com.luismejias.lumemedlink.shared.ChileanEmergency
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val URGENCY_TITLES: Map<OverbookUrgency, StringResource> = mapOf(
    OverbookUrgency.URGENT to Res.string.overbook_urgency_urgent,
    OverbookUrgency.PRIORITY to Res.string.overbook_urgency_priority,
    OverbookUrgency.NORMAL to Res.string.overbook_urgency_normal,
)

/** The platform's closed list, in its own words (ADR-0069 point 2). This sheet is the only place they are shown. */
private val REASON_TITLES: Map<OverbookReason, StringResource> = mapOf(
    OverbookReason.EARLIER_FOLLOW_UP to Res.string.overbook_reason_earlier_follow_up,
    OverbookReason.REVIEW_RESULTS to Res.string.overbook_reason_review_results,
    OverbookReason.RENEW_PRESCRIPTION to Res.string.overbook_reason_renew_prescription,
    OverbookReason.OTHER to Res.string.overbook_reason_other,
)

/**
 * «Pedir sobrecupo» (ADR-0069): the professional, where an emergency goes instead, how urgent and why — both from
 * closed lists, no free text — and how the practice answers. Sending needs both choices.
 */
@Composable
internal fun OverbookSheet(model: OverbookViewModel) {
    val state by model.state.collectAsState()
    val shown = state
    LumeSheet(
        visible = shown != null,
        onDismiss = model::dismiss,
        title = stringResource(Res.string.overbook_sheet_title),
        subtitle = shown?.doctor?.doctor?.displayName,
        art = shown?.let { { DoctorAvatar(it.doctor.doctor, LumeAvatarSize.Large) } },
        actions = {
            LumeStack(axis = LumeStackAxis.Horizontal, style = LumeStackStyle.Paired) {
                LumeButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.overbook_later)),
                    emphasis = LumeButtonEmphasis.OutlineNeutral,
                    size = LumeButtonSize.Bar,
                    width = LumeButtonWidth.Fill,
                    onClick = model::dismiss,
                )
                LumeAsyncButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.overbook_send)),
                    size = LumeButtonSize.Bar,
                    width = LumeButtonWidth.Fill,
                    state = if (shown?.draft != null) LumeFieldState.Enabled else LumeFieldState.Disabled,
                    run = model::send,
                )
            }
        },
    ) {
        if (shown == null) return@LumeSheet
        // First, before any choice: an urgency here is not an emergency (ADR-0069 point 4).
        LumeCallout(
            text = stringResource(Res.string.overbook_emergency, ChileanEmergency.AMBULANCE),
            style = LumeCalloutStyle.Warning,
            surface = LumeCalloutSurface.Washed,
            icon = LumeIcon.Alert,
        )
        Choices(Res.string.overbook_urgency_heading, URGENCY_TITLES, shown.urgency, model::urgencyChosen)
        Choices(Res.string.overbook_reason_heading, REASON_TITLES, shown.reason, model::reasonChosen)
        LumeText(
            text = stringResource(Res.string.overbook_how_answered),
            style = LumeTextStyle.TextSmall,
            color = LumeTextColor.Secondary,
        )
        // Inside the sheet: it draws over the screen, so a toast under it would never be seen.
        shown.failure?.let {
            LumeCallout(
                text = stringResource(failureText(it)),
                style = LumeCalloutStyle.Error,
                surface = LumeCalloutSurface.Washed,
                icon = LumeIcon.Alert,
            )
        }
    }
}

/** One closed list as single-choice chips under its heading. */
@Composable
private fun <T> Choices(heading: StringResource, titles: Map<T, StringResource>, chosen: T?, onChoose: (T) -> Unit) {
    LumeStack(alignment = LumeStackAlignment.Start, spacing = { small }) {
        LumeEyebrow(stringResource(heading))
        LumeStack(axis = LumeStackAxis.Horizontal, style = LumeStackStyle.Wrap, spacing = { 0.dp }) {
            titles.forEach { (value, resource) ->
                val title = stringResource(resource)
                LumeFilterChip(
                    title = title,
                    isSelected = value == chosen,
                    contentDescription = title,
                    look = LumeFilterChipLook.Washed,
                    onClick = { onChoose(value) },
                )
            }
        }
    }
}

private fun failureText(failure: OverbookFailure): StringResource = when (failure) {
    OverbookFailure.NotTaken, OverbookFailure.NoLongerWaiting -> Res.string.overbook_failed_not_taken
    OverbookFailure.AlreadyWaiting -> Res.string.overbook_failed_waiting
    OverbookFailure.Offline -> Res.string.overbook_failed_offline
    OverbookFailure.Unavailable -> Res.string.overbook_failed_unavailable
}
