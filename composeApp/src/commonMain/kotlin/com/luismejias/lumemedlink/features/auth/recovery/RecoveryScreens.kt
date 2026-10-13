package com.luismejias.lumemedlink.features.auth.recovery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerAlignment
import cl.lume.uicomposer.components.LumeContainerScroll
import cl.lume.uicomposer.components.LumeFieldSubmit
import cl.lume.uicomposer.components.LumeFieldValidation
import cl.lume.uicomposer.components.LumeForm
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.core.input.OneTimeCodeField
import com.luismejias.lumemedlink.core.input.SensitiveFieldFormat
import com.luismejias.lumemedlink.core.input.SensitiveFieldPurpose
import com.luismejias.lumemedlink.core.input.SensitiveTextField
import com.luismejias.lumemedlink.core.session.Clock
import com.luismejias.lumemedlink.features.auth.flow.AccessBack
import com.luismejias.lumemedlink.features.auth.flow.AccessHeader
import com.luismejias.lumemedlink.features.auth.flow.AccessPrimaryButton
import com.luismejias.lumemedlink.features.auth.flow.AccessTitle
import com.luismejias.lumemedlink.features.auth.flow.AccessTone
import com.luismejias.lumemedlink.features.auth.flow.NewPasswordFields
import com.luismejias.lumemedlink.features.auth.flow.OfflineBanner
import com.luismejias.lumemedlink.features.auth.flow.ResultActions
import com.luismejias.lumemedlink.features.auth.flow.clockText
import com.luismejias.lumemedlink.features.auth.flow.secondsUntil
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.code_expired_title
import com.luismejias.lumemedlink.resources.code_wrong_attempts
import com.luismejias.lumemedlink.resources.common_continue
import com.luismejias.lumemedlink.resources.common_retry
import com.luismejias.lumemedlink.resources.field_code
import com.luismejias.lumemedlink.resources.field_code_clear
import com.luismejias.lumemedlink.resources.field_password_new
import com.luismejias.lumemedlink.resources.field_rut
import com.luismejias.lumemedlink.resources.field_rut_invalid
import com.luismejias.lumemedlink.resources.field_rut_placeholder
import com.luismejias.lumemedlink.resources.recovery_body
import com.luismejias.lumemedlink.resources.recovery_code_body
import com.luismejias.lumemedlink.resources.recovery_code_expires_in
import com.luismejias.lumemedlink.resources.recovery_code_title
import com.luismejias.lumemedlink.resources.recovery_done_action
import com.luismejias.lumemedlink.resources.recovery_done_body
import com.luismejias.lumemedlink.resources.recovery_done_title
import com.luismejias.lumemedlink.resources.recovery_new_body
import com.luismejias.lumemedlink.resources.recovery_new_reused
import com.luismejias.lumemedlink.resources.recovery_new_title
import com.luismejias.lumemedlink.resources.recovery_save
import com.luismejias.lumemedlink.resources.recovery_saving
import com.luismejias.lumemedlink.resources.recovery_send
import com.luismejias.lumemedlink.resources.recovery_sending
import com.luismejias.lumemedlink.resources.recovery_title
import com.luismejias.lumemedlink.resources.recovery_verifying
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** 3a. No "Contáctanos" (ADR-0044, deviation 2): there is no support channel to send anyone to. */
@Composable
internal fun RecoveryRutScreen(model: RecoveryRutModel, retryEpoch: Int, onBack: (() -> Unit)?) {
    val state by model.state.collectAsState()
    LumeContainer(scroll = LumeContainerScroll.Scrollable) {
        LumeStack(spacing = { large }) {
            AccessBack(onBack)
            OfflineBanner(state.offline)
            AccessHeader(
                icon = LumeIcon.Lock,
                title = stringResource(Res.string.recovery_title),
                body = stringResource(Res.string.recovery_body),
            )
            SensitiveTextField(
                value = state.rut,
                onValueChange = model::rutChanged,
                purpose = SensitiveFieldPurpose.PERSONAL_DATA,
                format = SensitiveFieldFormat.RUT,
                label = stringResource(Res.string.field_rut),
                placeholder = stringResource(Res.string.field_rut_placeholder),
                validation = if (state.rutInvalid) {
                    LumeFieldValidation.Error(stringResource(Res.string.field_rut_invalid))
                } else {
                    LumeFieldValidation.Neutral
                },
                submit = LumeFieldSubmit.Go,
            )
            AccessPrimaryButton(
                title = stringResource(if (state.offline) Res.string.common_retry else Res.string.recovery_send),
                loadingMessage = stringResource(Res.string.recovery_sending),
                enabled = state.canSubmit,
                retryEpoch = retryEpoch,
                run = model::submit,
            )
        }
    }
}

/** 3b, "Vence en m:ss". The text never says whether the RUT has an account. */
@Composable
internal fun RecoveryCodeScreen(model: RecoveryCodeModel, clock: Clock, retryEpoch: Int, onBack: (() -> Unit)?) {
    val state by model.state.collectAsState()
    val entry = state.entry
    val wrong = entry.wrongAttemptsLeft
    val expiresIn = secondsUntil(state.dispatch?.expiresAtEpochMillis, clock) ?: 0L
    LumeContainer(
        scroll = LumeContainerScroll.Scrollable,
        action = {
            AccessPrimaryButton(
                title = stringResource(if (entry.offline) Res.string.common_retry else Res.string.common_continue),
                loadingMessage = stringResource(Res.string.recovery_verifying),
                enabled = entry.complete && wrong == null && !entry.expired,
                retryEpoch = retryEpoch,
                run = model::submit,
            )
        },
    ) {
        LumeStack(spacing = { large }) {
            AccessBack(onBack)
            OfflineBanner(entry.offline)
            AccessHeader(
                icon = LumeIcon.Mobile,
                title = stringResource(Res.string.recovery_code_title),
                body = stringResource(Res.string.recovery_code_body),
            )
            OneTimeCodeField(
                code = entry.code,
                onCodeChange = model::codeChanged,
                label = stringResource(Res.string.field_code),
                clearContentDescription = stringResource(Res.string.field_code_clear),
                validation = when {
                    entry.expired -> LumeFieldValidation.Error(stringResource(Res.string.code_expired_title))
                    wrong != null ->
                        LumeFieldValidation.Error(pluralStringResource(Res.plurals.code_wrong_attempts, wrong, wrong))
                    else -> LumeFieldValidation.Neutral
                },
            )
            LumeText(
                text = stringResource(Res.string.recovery_code_expires_in, clockText(expiresIn)),
                color = LumeTextColor.Secondary,
            )
        }
    }
}

/** 3c. */
@Composable
internal fun NewPasswordScreen(model: NewPasswordModel, retryEpoch: Int, onBack: (() -> Unit)?) {
    val state by model.state.collectAsState()
    LumeForm(
        title = null,
        action = {
            AccessPrimaryButton(
                title = stringResource(if (state.offline) Res.string.common_retry else Res.string.recovery_save),
                loadingMessage = stringResource(Res.string.recovery_saving),
                enabled = state.canSubmit,
                retryEpoch = retryEpoch,
                run = model::submit,
            )
        },
    ) {
        AccessBack(onBack)
        OfflineBanner(state.offline)
        AccessTitle(stringResource(Res.string.recovery_new_title), stringResource(Res.string.recovery_new_body))
        NewPasswordFields(
            entry = state.entry,
            onPassword = model::passwordChanged,
            onRepeated = model::repeatedChanged,
            passwordLabel = stringResource(Res.string.field_password_new),
            extraError = if (state.reused) stringResource(Res.string.recovery_new_reused) else null,
        )
    }
}

/** 3d. */
@Composable
internal fun RecoveryDoneScreen(onSignIn: () -> Unit) {
    LumeContainer(
        alignment = LumeContainerAlignment.CenterAbovePinnedAction,
        action = { ResultActions(primary = stringResource(Res.string.recovery_done_action), onPrimary = onSignIn) },
    ) {
        AccessHeader(
            icon = LumeIcon.Check,
            title = stringResource(Res.string.recovery_done_title),
            body = stringResource(Res.string.recovery_done_body),
            tone = AccessTone.SUCCESS,
        )
    }
}
