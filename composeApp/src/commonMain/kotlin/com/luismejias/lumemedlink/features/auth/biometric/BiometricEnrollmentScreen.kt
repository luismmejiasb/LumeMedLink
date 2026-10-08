package com.luismejias.lumemedlink.features.auth.biometric

import androidx.compose.runtime.Composable
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTextStyle
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.core.session.SessionEntryOutcome
import com.luismejias.lumemedlink.core.session.SessionTokens
import com.luismejias.lumemedlink.features.auth.flow.AuthCopy
import com.luismejias.lumemedlink.features.auth.flow.AuthHeader
import com.luismejias.lumemedlink.features.auth.flow.AuthPrimaryButton

/**
 * LumeMed's `FaceIDEnrollmentFormView`, with the author's rule: no way past it without biometrics. And the
 * sentence ADR-0041 asks the sign-in to say — the phone is the person's alone, because any finger or face
 * registered on it opens the session.
 */
@Composable
internal fun BiometricEnrollmentScreen(
    model: BiometricEnrollmentModel,
    establish: suspend (SessionTokens) -> SessionEntryOutcome,
    onEstablished: () -> Unit,
    retryEpoch: Int,
) {
    LumeStack(spacing = { large }) {
        AuthHeader(icon = LumeIcon.Shield, title = AuthCopy.BIOMETRIC_TITLE, body = AuthCopy.BIOMETRIC_BODY)
        LumeText(text = AuthCopy.BIOMETRIC_PERSONAL, style = LumeTextStyle.Caption, color = LumeTextColor.Secondary)
        AuthPrimaryButton(
            title = AuthCopy.BIOMETRIC_ACTIVATE,
            loadingMessage = AuthCopy.BIOMETRIC_ACTIVATING,
            enabled = true,
            retryEpoch = retryEpoch,
            run = { model.activate(establish, onEstablished) },
        )
    }
}
