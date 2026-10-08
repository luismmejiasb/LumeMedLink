package com.luismejias.lumemedlink.features.auth.mfa

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.core.input.OneTimeCodeField
import com.luismejias.lumemedlink.features.auth.flow.AuthCopy
import com.luismejias.lumemedlink.features.auth.flow.AuthHeader
import com.luismejias.lumemedlink.features.auth.flow.AuthPrimaryButton

/**
 * LumeMed's code steps (`MFAChallengeFormView`, `TOTPEnrollmentConfirmFormView`, the reset code): the head,
 * the six-box code field, and the action. LumeMed's "confiar en este dispositivo" switch is not drawn: it
 * skips the second factor for thirty days, a session policy this repo has not decided.
 */
@Composable
internal fun <T> CodeStepScreen(
    model: CodeStepModel<T>,
    title: String,
    body: String,
    action: String,
    running: String,
    icon: LumeIcon,
    retryEpoch: Int,
) {
    val code by model.code.collectAsState()
    LumeStack(spacing = { large }) {
        AuthHeader(icon = icon, title = title, body = body)
        OneTimeCodeField(
            code = code,
            onCodeChange = model::codeChanged,
            label = AuthCopy.CODE_LABEL,
            clearContentDescription = AuthCopy.CODE_CLEAR,
        )
        AuthPrimaryButton(
            title = action,
            loadingMessage = running,
            enabled = model.canSubmit,
            retryEpoch = retryEpoch,
            run = model::submit,
        )
    }
}
