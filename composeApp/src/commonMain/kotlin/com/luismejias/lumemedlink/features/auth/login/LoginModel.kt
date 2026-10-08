package com.luismejias.lumemedlink.features.auth.login

import com.luismejias.lumemedlink.core.auth.AuthFailure
import com.luismejias.lumemedlink.core.auth.AuthGateway
import com.luismejias.lumemedlink.core.auth.AuthResult
import com.luismejias.lumemedlink.core.auth.SignInNextStep
import com.luismejias.lumemedlink.features.auth.flow.AuthCopy
import com.luismejias.lumemedlink.features.auth.flow.AuthFlowModel
import com.luismejias.lumemedlink.features.auth.flow.AuthRoute
import com.luismejias.lumemedlink.features.auth.flow.StepOutcome
import com.luismejias.lumemedlink.shared.ChileanRut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A RUT is judged only once it is long enough to be one, as in LumeMed: no red while the person types. */
private const val RUT_JUDGED_FROM = 8

internal data class LoginState(val rut: String = "", val password: String = "") {
    private val parsed: ChileanRut? get() = ChileanRut.parse(rut)

    /** `true` when the RUT should show its error: long enough to judge, and not a valid RUT. */
    val rutInvalid: Boolean get() = rut.count { it.isLetterOrDigit() } >= RUT_JUDGED_FROM && parsed == null

    val canSubmit: Boolean get() = parsed != null && password.isNotEmpty()

    // Never prints what was typed: this is a RUT and a password (§8.1).
    override fun toString(): String = "LoginState(redacted)"
}

/**
 * The sign-in screen (LumeMed's `LoginViewModel`): RUT and password, and the server says what comes next —
 * the authenticator's code, or linking one first. No session exists after this step.
 */
internal class LoginModel(private val gateway: AuthGateway, private val flow: AuthFlowModel) {
    private val current = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = current.asStateFlow()

    fun rutChanged(value: String) {
        current.value = current.value.copy(rut = value)
    }

    fun passwordChanged(value: String) {
        current.value = current.value.copy(password = value)
    }

    suspend fun submit(): StepOutcome {
        val typed = current.value
        val rut = ChileanRut.parse(typed.rut) ?: return StepOutcome.Rejected(AuthCopy.RUT_INVALID)
        if (typed.password.isEmpty()) return StepOutcome.Rejected(AuthCopy.WRONG_CREDENTIALS)
        return when (val result = gateway.signIn(rut, typed.password)) {
            is AuthResult.Ok -> {
                // The password is spent: it leaves memory the moment it is used.
                current.value = typed.copy(password = "")
                flow.push(
                    when (result.value) {
                        SignInNextStep.TOTP_CHALLENGE -> AuthRoute.MFA_CHALLENGE
                        SignInNextStep.TOTP_ENROLLMENT -> AuthRoute.TOTP_ENROLLMENT
                    },
                )
                StepOutcome.Advanced
            }
            is AuthResult.Failed -> when (result.failure) {
                AuthFailure.InvalidCredentials, AuthFailure.InvalidCode -> StepOutcome.Rejected(
                    AuthCopy.WRONG_CREDENTIALS,
                )
                else -> flow.blockedBy(result.failure)
            }
        }
    }
}
