package com.luismejias.lumemedlink.features.patient.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cl.lume.uicomposer.components.LumeAsyncButton
import cl.lume.uicomposer.components.LumeButton
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeButtonEmphasis
import cl.lume.uicomposer.components.LumeButtonSize
import cl.lume.uicomposer.components.LumeButtonWidth
import cl.lume.uicomposer.components.LumeCallout
import cl.lume.uicomposer.components.LumeCalloutStyle
import cl.lume.uicomposer.components.LumeCalloutSurface
import cl.lume.uicomposer.components.LumeFieldState
import cl.lume.uicomposer.components.LumeFieldValidation
import cl.lume.uicomposer.components.LumeLink
import cl.lume.uicomposer.components.LumeRegion
import cl.lume.uicomposer.components.LumeSheet
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeStackAxis
import cl.lume.uicomposer.components.LumeStackStyle
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.core.care.ContactKind
import com.luismejias.lumemedlink.core.input.OneTimeCodeField
import com.luismejias.lumemedlink.core.input.SensitiveFieldFormat
import com.luismejias.lumemedlink.core.input.SensitiveFieldPurpose
import com.luismejias.lumemedlink.core.input.SensitivePhoneField
import com.luismejias.lumemedlink.core.input.SensitiveTextField
import com.luismejias.lumemedlink.core.screen.clockText
import com.luismejias.lumemedlink.core.screen.secondsUntil
import com.luismejias.lumemedlink.core.session.SystemClock
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.code_wrong_attempts
import com.luismejias.lumemedlink.resources.common_cancel
import com.luismejias.lumemedlink.resources.contact_code_body
import com.luismejias.lumemedlink.resources.contact_confirm
import com.luismejias.lumemedlink.resources.contact_email_body
import com.luismejias.lumemedlink.resources.contact_email_title
import com.luismejias.lumemedlink.resources.contact_expired
import com.luismejias.lumemedlink.resources.contact_new_email
import com.luismejias.lumemedlink.resources.contact_new_phone
import com.luismejias.lumemedlink.resources.contact_offline
import com.luismejias.lumemedlink.resources.contact_phone_body
import com.luismejias.lumemedlink.resources.contact_phone_title
import com.luismejias.lumemedlink.resources.contact_same_email
import com.luismejias.lumemedlink.resources.contact_same_phone
import com.luismejias.lumemedlink.resources.contact_send
import com.luismejias.lumemedlink.resources.contact_unavailable
import com.luismejias.lumemedlink.resources.field_code
import com.luismejias.lumemedlink.resources.field_code_clear
import com.luismejias.lumemedlink.resources.field_email_invalid
import com.luismejias.lumemedlink.resources.field_mobile_invalid
import com.luismejias.lumemedlink.resources.field_phone_invalid
import com.luismejias.lumemedlink.resources.sign_up_code_resend
import com.luismejias.lumemedlink.resources.sign_up_code_resend_in
import com.luismejias.lumemedlink.shared.PhoneNumber
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * «Cambiar teléfono» and «Cambiar correo» (task 0035): the new contact, then the code sent to it, in one sheet that is
 * always composed — [ContactChangeViewModel.state] says whether it is up, so the kit can animate it in and out.
 */
@Composable
internal fun ContactChangeSheet(model: ContactChangeViewModel) {
    val state by model.state.collectAsState()
    val shown = state
    val phone = shown?.kind == ContactKind.PHONE
    LumeSheet(
        visible = shown != null,
        onDismiss = model::dismiss,
        title = stringResource(if (phone) Res.string.contact_phone_title else Res.string.contact_email_title),
        actions = { ContactChangeActions(model, shown) },
    ) {
        if (shown == null) return@LumeSheet
        when (shown.step) {
            ContactChangeStep.ENTER -> EnterStep(model, shown)
            ContactChangeStep.CODE -> CodeStep(model, shown)
        }
        shown.trouble?.let {
            LumeCallout(
                text = stringResource(
                    if (it ==
                        ContactChangeTrouble.OFFLINE
                    ) {
                        Res.string.contact_offline
                    } else {
                        Res.string.contact_unavailable
                    },
                ),
                style = LumeCalloutStyle.Error,
                surface = LumeCalloutSurface.Washed,
                icon = LumeIcon.Alert,
            )
        }
    }
}

@Composable
private fun EnterStep(model: ContactChangeViewModel, shown: ContactChangeState) {
    val phone = shown.kind == ContactKind.PHONE
    LumeText(
        text = stringResource(if (phone) Res.string.contact_phone_body else Res.string.contact_email_body),
        color = LumeTextColor.Secondary,
    )
    val validation = when {
        shown.sameAsCurrent -> LumeFieldValidation.Error(
            stringResource(if (phone) Res.string.contact_same_phone else Res.string.contact_same_email),
        )
        !shown.invalid -> LumeFieldValidation.Neutral
        !phone -> LumeFieldValidation.Error(stringResource(Res.string.field_email_invalid))
        shown.dialCode == PhoneNumber.CHILE -> LumeFieldValidation.Error(
            stringResource(Res.string.field_mobile_invalid),
        )
        else -> LumeFieldValidation.Error(stringResource(Res.string.field_phone_invalid))
    }
    if (phone) {
        SensitivePhoneField(
            value = shown.typed,
            onValueChange = model::typedChanged,
            region = LumeRegion.of(shown.region) ?: requireNotNull(LumeRegion.of(CONTACT_CHANGE_REGION)),
            onRegionChange = { model.regionChanged(it.code, it.dialCode) },
            label = stringResource(Res.string.contact_new_phone),
            placeholder = "",
            modifier = Modifier.fillMaxWidth(),
            groups = if (shown.dialCode == PhoneNumber.CHILE) PhoneNumber.CHILEAN_MOBILE_GROUPS else null,
            validation = validation,
            onSubmit = { model.launch { send() } },
        )
    } else {
        SensitiveTextField(
            value = shown.typed,
            onValueChange = model::typedChanged,
            purpose = SensitiveFieldPurpose.PERSONAL_DATA,
            format = SensitiveFieldFormat.EMAIL,
            label = stringResource(Res.string.contact_new_email),
            placeholder = "",
            modifier = Modifier.fillMaxWidth(),
            validation = validation,
            onSubmit = { model.launch { send() } },
        )
    }
}

@Composable
private fun CodeStep(model: ContactChangeViewModel, shown: ContactChangeState) {
    LumeText(text = stringResource(Res.string.contact_code_body, shown.destination), color = LumeTextColor.Secondary)
    val wrong = shown.wrongAttemptsLeft
    OneTimeCodeField(
        code = shown.code,
        onCodeChange = model::codeChanged,
        label = stringResource(Res.string.field_code),
        clearContentDescription = stringResource(Res.string.field_code_clear),
        modifier = Modifier.fillMaxWidth(),
        validation = when {
            wrong != null -> LumeFieldValidation.Error(
                pluralStringResource(Res.plurals.code_wrong_attempts, wrong, wrong),
            )
            shown.expired -> LumeFieldValidation.Error(stringResource(Res.string.contact_expired))
            else -> LumeFieldValidation.Neutral
        },
    )
    // A lapsed code may be replaced at once; otherwise the resend waits its interval, as the sign-up's does.
    val resendIn = if (shown.expired) {
        0L
    } else {
        secondsUntil(shown.dispatch?.resendAvailableAtEpochMillis, SystemClock)
            ?: 0L
    }
    LumeStack(modifier = Modifier.fillMaxWidth(), alignment = LumeStackAlignment.Start) {
        if (resendIn > 0) {
            LumeText(
                text = stringResource(Res.string.sign_up_code_resend_in, clockText(resendIn)),
                color = LumeTextColor.Secondary,
            )
        } else {
            LumeLink(title = stringResource(Res.string.sign_up_code_resend), onClick = { model.launch { resend() } })
        }
    }
}

/** «Cancelar» beside the step's action: «Enviar código», then «Confirmar». */
@Composable
private fun ContactChangeActions(model: ContactChangeViewModel, shown: ContactChangeState?) {
    val entering = shown?.step != ContactChangeStep.CODE
    val enabled = when {
        shown == null -> false
        entering -> shown.canSend
        else -> shown.canConfirm
    }
    LumeStack(axis = LumeStackAxis.Horizontal, style = LumeStackStyle.Paired) {
        LumeButton(
            content = LumeButtonContent.Title(stringResource(Res.string.common_cancel)),
            emphasis = LumeButtonEmphasis.OutlineNeutral,
            size = LumeButtonSize.Bar,
            width = LumeButtonWidth.Fill,
            onClick = model::dismiss,
        )
        LumeAsyncButton(
            content = LumeButtonContent.Title(
                stringResource(if (entering) Res.string.contact_send else Res.string.contact_confirm),
            ),
            size = LumeButtonSize.Bar,
            width = LumeButtonWidth.Fill,
            state = if (enabled) LumeFieldState.Enabled else LumeFieldState.Disabled,
            run = { if (entering) model.send() else model.confirm() },
        )
    }
}
