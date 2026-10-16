package com.luismejias.lumemedlink.features.patient.appointments

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cl.lume.uicomposer.components.LumeButton
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeButtonEmphasis
import cl.lume.uicomposer.components.LumeButtonSize
import cl.lume.uicomposer.components.LumeCallout
import cl.lume.uicomposer.components.LumeCalloutStyle
import cl.lume.uicomposer.components.LumeCalloutSurface
import cl.lume.uicomposer.components.LumeCard
import cl.lume.uicomposer.components.LumeCardStyle
import cl.lume.uicomposer.components.LumeList
import cl.lume.uicomposer.components.LumeListAccessory
import cl.lume.uicomposer.components.LumeListRow
import cl.lume.uicomposer.components.LumeListRowTextStyle
import cl.lume.uicomposer.components.LumeListStyle
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeTagStyle
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTextStyle
import com.luismejias.lumemedlink.core.care.OverbookFailure
import com.luismejias.lumemedlink.core.care.OverbookRequest
import com.luismejias.lumemedlink.core.care.OverbookRequestStatus
import com.luismejias.lumemedlink.core.screen.shortDateText
import com.luismejias.lumemedlink.features.patient.parts.doctorMarker
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.overbook_asked_on
import com.luismejias.lumemedlink.resources.overbook_by_phone
import com.luismejias.lumemedlink.resources.overbook_granted_note
import com.luismejias.lumemedlink.resources.overbook_status_declined
import com.luismejias.lumemedlink.resources.overbook_status_granted
import com.luismejias.lumemedlink.resources.overbook_status_pending
import com.luismejias.lumemedlink.resources.overbook_status_withdrawn
import com.luismejias.lumemedlink.resources.overbook_unnamed
import com.luismejias.lumemedlink.resources.overbook_withdraw
import com.luismejias.lumemedlink.resources.overbook_withdraw_failed
import com.luismejias.lumemedlink.resources.overbooks_title
import org.jetbrains.compose.resources.stringResource

/**
 * «Solicitudes de sobrecupo» on «Citas» (ADR-0069, its placement of 2026-10-16): who, where, how it stands and when it
 * was asked. Never the reason nor the urgency, which this screen does not even receive. A waiting request can be taken
 * back when [onWithdraw] is given; with overbook requests off, the host draws no section at all.
 */
@Composable
internal fun OverbookSection(
    requests: List<OverbookRequest>,
    withdrawalFailure: OverbookFailure?,
    onWithdraw: ((OverbookRequest) -> Unit)?,
) {
    if (requests.isEmpty()) return
    LumeStack(alignment = LumeStackAlignment.Start, spacing = { small }) {
        LumeText(text = stringResource(Res.string.overbooks_title), style = LumeTextStyle.HeadingSmall)
        if (withdrawalFailure != null) {
            LumeCallout(
                text = stringResource(Res.string.overbook_withdraw_failed),
                style = LumeCalloutStyle.Error,
                surface = LumeCalloutSurface.Washed,
            )
        }
        requests.forEach { request -> OverbookCard(request, onWithdraw) }
    }
}

@Composable
private fun OverbookCard(request: OverbookRequest, onWithdraw: ((OverbookRequest) -> Unit)?) {
    val unnamed = stringResource(Res.string.overbook_unnamed)
    val asked = stringResource(Res.string.overbook_asked_on, shortDateText(request.askedOn))
    val byPhone = stringResource(Res.string.overbook_by_phone)
    LumeCard(modifier = Modifier.fillMaxWidth(), style = LumeCardStyle.Filled) {
        LumeStack(alignment = LumeStackAlignment.Start, spacing = { small }) {
            LumeList(items = listOf(request), key = { it.id }, style = LumeListStyle.Embedded) {
                LumeListRow(
                    textStyle = LumeListRowTextStyle.Strong,
                    title = it.doctor?.displayName ?: unnamed,
                    description = listOf(it.clinic, asked).filter(String::isNotBlank).joinToString(" · ") +
                        if (it.byPhone) "\n$byPhone" else "",
                    leading = it.doctor?.let(::doctorMarker) ?: LumeListAccessory.None,
                    trailing = overbookStatusAccessory(it.status),
                )
            }
            if (request.status == OverbookRequestStatus.GRANTED) {
                LumeText(text = stringResource(Res.string.overbook_granted_note), color = LumeTextColor.Secondary)
            }
            if (request.waiting && onWithdraw != null) {
                LumeButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.overbook_withdraw)),
                    emphasis = LumeButtonEmphasis.OutlineNeutral,
                    size = LumeButtonSize.Card,
                    onClick = { onWithdraw(request) },
                )
            }
        }
    }
}

/** The request's verdict as a tag; none for a status this app does not know (the contract's open vocabulary). */
@Composable
private fun overbookStatusAccessory(status: OverbookRequestStatus): LumeListAccessory = when (status) {
    OverbookRequestStatus.PENDING ->
        LumeListAccessory.Tag(stringResource(Res.string.overbook_status_pending), LumeTagStyle.Warning)
    OverbookRequestStatus.GRANTED ->
        LumeListAccessory.Tag(stringResource(Res.string.overbook_status_granted), LumeTagStyle.Success)
    OverbookRequestStatus.DECLINED ->
        LumeListAccessory.Tag(stringResource(Res.string.overbook_status_declined), LumeTagStyle.Error)
    OverbookRequestStatus.WITHDRAWN ->
        LumeListAccessory.Tag(stringResource(Res.string.overbook_status_withdrawn), LumeTagStyle.Neutral)
    OverbookRequestStatus.OTHER -> LumeListAccessory.None
}
