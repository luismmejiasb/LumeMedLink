package com.luismejias.lumemedlink.core.input

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cl.lume.uicomposer.components.LumeFieldState
import cl.lume.uicomposer.components.LumeFieldValidation
import cl.lume.uicomposer.components.LumeOTPField

/** The length of every one-time code this app asks for: an authenticator's TOTP and the reset code. */
internal const val ONE_TIME_CODE_DIGITS = 6

/**
 * A one-time code — the authenticator's TOTP, a password-reset code — through the kit's code field
 * (ADR-0013: a kit input is reached only from `core/input`).
 *
 * Nothing to decide about the keyboard here, and that is why it is its own entry instead of a purpose of
 * [SensitiveTextField]: the kit's code field is already the digit pad that does not learn, keeps the code
 * in plain state (never in saved state, so never on disk), logs nothing, and on iOS asks for
 * `oneTimeCode` so the platform can offer a code from Messages. A code is spent the moment it is
 * verified; what this entry adds is the one length the whole app uses.
 */
@Composable
internal fun OneTimeCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    label: String,
    clearContentDescription: String,
    modifier: Modifier = Modifier,
    state: LumeFieldState = LumeFieldState.Enabled,
    validation: LumeFieldValidation = LumeFieldValidation.Neutral,
    onComplete: ((String) -> Unit)? = null,
) {
    LumeOTPField(
        code = code,
        onCodeChange = onCodeChange,
        label = label,
        modifier = modifier,
        digits = ONE_TIME_CODE_DIGITS,
        clearContentDescription = clearContentDescription,
        state = state,
        validation = validation,
        onComplete = onComplete,
    )
}
