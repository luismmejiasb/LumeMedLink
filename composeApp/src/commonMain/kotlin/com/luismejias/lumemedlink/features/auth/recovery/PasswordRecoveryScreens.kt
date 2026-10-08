package com.luismejias.lumemedlink.features.auth.recovery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cl.lume.uicomposer.components.LumeFieldGroup
import cl.lume.uicomposer.components.LumeFieldSubmit
import cl.lume.uicomposer.components.LumeFieldValidation
import cl.lume.uicomposer.components.LumeRevealLabels
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.core.input.SensitiveFieldFormat
import com.luismejias.lumemedlink.core.input.SensitiveFieldPurpose
import com.luismejias.lumemedlink.core.input.SensitiveTextField
import com.luismejias.lumemedlink.features.auth.flow.AuthCopy
import com.luismejias.lumemedlink.features.auth.flow.AuthHeader
import com.luismejias.lumemedlink.features.auth.flow.AuthPrimaryButton

/** LumeMed's `PasswordRecoveryFormView`: envelope, title, why, the RUT, "Enviar código". */
@Composable
internal fun PasswordRecoveryScreen(model: PasswordRecoveryModel, retryEpoch: Int) {
    val rut by model.rut.collectAsState()
    LumeStack(spacing = { large }) {
        AuthHeader(icon = LumeIcon.Mail, title = AuthCopy.RECOVERY_TITLE, body = AuthCopy.RECOVERY_BODY)
        LumeFieldGroup {
            SensitiveTextField(
                value = rut,
                onValueChange = model::rutChanged,
                purpose = SensitiveFieldPurpose.PERSONAL_DATA,
                format = SensitiveFieldFormat.RUT,
                label = AuthCopy.RUT_LABEL,
                placeholder = AuthCopy.RUT_PLACEHOLDER,
                submit = LumeFieldSubmit.Go,
            )
        }
        AuthPrimaryButton(
            title = AuthCopy.SEND_CODE,
            loadingMessage = AuthCopy.SENDING,
            enabled = model.canSubmit,
            retryEpoch = retryEpoch,
            run = model::submit,
        )
    }
}

/**
 * The new password, twice. LumeMed left this step as a placeholder; here it is the smallest honest version:
 * both entries must match, the policy is the IdP's, and the field asks the platform for a NEW credential.
 */
@Composable
internal fun NewPasswordScreen(model: NewPasswordModel, retryEpoch: Int) {
    val state by model.state.collectAsState()
    val reveal = LumeRevealLabels(show = AuthCopy.PASSWORD_SHOW, hide = AuthCopy.PASSWORD_HIDE)
    LumeStack(spacing = { large }) {
        AuthHeader(icon = LumeIcon.Key, title = AuthCopy.NEW_PASSWORD_TITLE, body = AuthCopy.NEW_PASSWORD_BODY)
        LumeFieldGroup {
            SensitiveTextField(
                value = state.password,
                onValueChange = model::passwordChanged,
                purpose = SensitiveFieldPurpose.CREDENTIAL,
                label = AuthCopy.NEW_PASSWORD_LABEL,
                placeholder = AuthCopy.PASSWORD_PLACEHOLDER,
                reveal = reveal,
                newCredential = true,
                submit = LumeFieldSubmit.Next,
            )
            SensitiveTextField(
                value = state.repeated,
                onValueChange = model::repeatedChanged,
                purpose = SensitiveFieldPurpose.CREDENTIAL,
                label = AuthCopy.NEW_PASSWORD_REPEAT,
                placeholder = AuthCopy.PASSWORD_PLACEHOLDER,
                reveal = reveal,
                newCredential = true,
                validation = if (state.mismatch) {
                    LumeFieldValidation.Error(
                        AuthCopy.PASSWORDS_DIFFER,
                    )
                } else {
                    LumeFieldValidation.Neutral
                },
                submit = LumeFieldSubmit.Go,
            )
        }
        AuthPrimaryButton(
            title = AuthCopy.SAVE_PASSWORD,
            loadingMessage = AuthCopy.SAVING,
            enabled = state.canSubmit,
            retryEpoch = retryEpoch,
            run = model::submit,
        )
    }
}
