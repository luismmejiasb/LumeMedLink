package com.luismejias.lumemedlink.features.auth.mfa

import com.luismejias.lumemedlink.core.auth.AuthFailure
import com.luismejias.lumemedlink.core.auth.AuthResult
import com.luismejias.lumemedlink.core.input.ONE_TIME_CODE_DIGITS
import com.luismejias.lumemedlink.features.auth.flow.AuthCopy
import com.luismejias.lumemedlink.features.auth.flow.AuthFlowModel
import com.luismejias.lumemedlink.features.auth.flow.StepOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A step that sends a six-digit code and moves on when the server accepts it: the authenticator's code at
 * sign-in, the one that confirms a new authenticator, the password-reset code. One model, three uses —
 * what differs is what the server is asked and what happens next.
 *
 * A refused code is cleared, so the next attempt starts from empty boxes rather than editing a spent one.
 */
internal class CodeStepModel<T>(
    private val flow: AuthFlowModel,
    private val send: suspend (String) -> AuthResult<T>,
    private val onAccepted: (T) -> Unit,
) {
    private val current = MutableStateFlow("")
    val code: StateFlow<String> = current.asStateFlow()

    val canSubmit: Boolean get() = current.value.length == ONE_TIME_CODE_DIGITS

    fun codeChanged(value: String) {
        current.value = value.filter { it.isDigit() }.take(ONE_TIME_CODE_DIGITS)
    }

    suspend fun submit(): StepOutcome {
        val typed = current.value
        if (typed.length != ONE_TIME_CODE_DIGITS) return StepOutcome.Rejected(AuthCopy.WRONG_CODE)
        return when (val result = send(typed)) {
            is AuthResult.Ok -> {
                current.value = ""
                onAccepted(result.value)
                StepOutcome.Advanced
            }
            is AuthResult.Failed -> when (result.failure) {
                AuthFailure.InvalidCode, AuthFailure.InvalidCredentials -> {
                    current.value = ""
                    StepOutcome.Rejected(AuthCopy.WRONG_CODE)
                }
                else -> flow.blockedBy(result.failure)
            }
        }
    }
}
