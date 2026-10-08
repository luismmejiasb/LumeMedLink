package com.luismejias.lumemedlink.features.auth.totp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cl.lume.uicomposer.components.LumeButton
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeButtonWidth
import cl.lume.uicomposer.components.LumeFieldState
import cl.lume.uicomposer.components.LumeQRCode
import cl.lume.uicomposer.components.LumeReadOnlyField
import cl.lume.uicomposer.components.LumeReadOnlyFieldDesign
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTextStyle
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.features.auth.flow.AuthCopy
import com.luismejias.lumemedlink.features.auth.flow.AuthHeader

/**
 * LumeMed's `TOTPEnrollmentFormView`: the head, the QR, the key as a read-out, the note that a new one
 * replaces the old, and "Continuar" — disabled until the material arrived. The key has no copy action
 * (§8.9; see [TotpEnrollmentModel]).
 */
@Composable
internal fun TotpEnrollmentScreen(model: TotpEnrollmentModel, retryEpoch: Int) {
    val material by model.material.collectAsState()
    // Loads on arrival, and again when the alert's retry bumps the epoch.
    LaunchedEffect(retryEpoch) { model.load() }
    LumeStack(spacing = { large }) {
        AuthHeader(icon = LumeIcon.Key, title = AuthCopy.TOTP_TITLE, body = AuthCopy.TOTP_BODY)
        val shown = material
        if (shown != null) {
            LumeQRCode(payload = shown.otpauthUri, accessibilityLabel = AuthCopy.TOTP_QR)
        }
        LumeReadOnlyField(
            value = shown?.secret,
            placeholder = AuthCopy.TOTP_LOADING,
            title = AuthCopy.TOTP_KEY,
            footnote = AuthCopy.TOTP_KEY_FOOTNOTE,
            design = LumeReadOnlyFieldDesign.Code,
        )
        LumeText(text = AuthCopy.TOTP_REPLACES, style = LumeTextStyle.Caption, color = LumeTextColor.Secondary)
        LumeButton(
            content = LumeButtonContent.Title(AuthCopy.CONTINUE),
            width = LumeButtonWidth.Fill,
            state = if (shown != null) LumeFieldState.Enabled else LumeFieldState.Disabled,
            onClick = model::proceed,
        )
    }
}
