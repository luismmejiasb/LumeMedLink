package com.luismejias.lumemedlink.features.auth.login

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cl.lume.uicomposer.components.LumeDivider
import cl.lume.uicomposer.components.LumeFieldGroup
import cl.lume.uicomposer.components.LumeFieldSubmit
import cl.lume.uicomposer.components.LumeFieldValidation
import cl.lume.uicomposer.components.LumeLink
import cl.lume.uicomposer.components.LumeRevealLabels
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextStyle
import com.luismejias.lumemedlink.core.input.SensitiveFieldFormat
import com.luismejias.lumemedlink.core.input.SensitiveFieldPurpose
import com.luismejias.lumemedlink.core.input.SensitiveTextField
import com.luismejias.lumemedlink.features.auth.flow.AuthCopy
import com.luismejias.lumemedlink.features.auth.flow.AuthFlowModel
import com.luismejias.lumemedlink.features.auth.flow.AuthPrimaryButton
import com.luismejias.lumemedlink.features.auth.flow.AuthRoute

/**
 * LumeMed's `LoginFormView`, in this app: the name as header over a divider, the RUT and the password
 * (with its show/hide control), "¿Olvidaste tu contraseña?" at the trailing edge, and the one filled action.
 * LumeMed's support line is not drawn: this app has no support channel yet, and a link to nowhere is worse
 * than none.
 */
@Composable
internal fun LoginScreen(model: LoginModel, flow: AuthFlowModel, retryEpoch: Int) {
    val state by model.state.collectAsState()
    LumeStack(spacing = { large }) {
        LumeText(text = AuthCopy.APP_NAME, style = LumeTextStyle.HeadingLarge)
        LumeDivider()
        LumeFieldGroup {
            SensitiveTextField(
                value = state.rut,
                onValueChange = model::rutChanged,
                purpose = SensitiveFieldPurpose.PERSONAL_DATA,
                format = SensitiveFieldFormat.RUT,
                label = AuthCopy.RUT_LABEL,
                placeholder = AuthCopy.RUT_PLACEHOLDER,
                validation = if (state.rutInvalid) {
                    LumeFieldValidation.Error(
                        AuthCopy.RUT_INVALID,
                    )
                } else {
                    LumeFieldValidation.Neutral
                },
                submit = LumeFieldSubmit.Next,
            )
            SensitiveTextField(
                value = state.password,
                onValueChange = model::passwordChanged,
                purpose = SensitiveFieldPurpose.CREDENTIAL,
                label = AuthCopy.PASSWORD_LABEL,
                placeholder = AuthCopy.PASSWORD_PLACEHOLDER,
                reveal = LumeRevealLabels(show = AuthCopy.PASSWORD_SHOW, hide = AuthCopy.PASSWORD_HIDE),
                submit = LumeFieldSubmit.Go,
            )
        }
        // Full width so the link reaches the trailing edge, as in LumeMed; a hugging stack centres it.
        LumeStack(modifier = Modifier.fillMaxWidth(), alignment = LumeStackAlignment.End) {
            LumeLink(title = AuthCopy.FORGOT_PASSWORD, onClick = { flow.push(AuthRoute.PASSWORD_RECOVERY) })
        }
        AuthPrimaryButton(
            title = AuthCopy.SIGN_IN,
            loadingMessage = AuthCopy.SIGNING_IN,
            enabled = state.canSubmit,
            retryEpoch = retryEpoch,
            run = model::submit,
        )
    }
}
