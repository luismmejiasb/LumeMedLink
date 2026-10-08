package com.luismejias.lumemedlink.features.auth.recovery

import com.luismejias.lumemedlink.core.auth.AuthGateway
import com.luismejias.lumemedlink.core.auth.AuthResult
import com.luismejias.lumemedlink.features.auth.flow.AuthCopy
import com.luismejias.lumemedlink.features.auth.flow.AuthFlowModel
import com.luismejias.lumemedlink.features.auth.flow.AuthRoute
import com.luismejias.lumemedlink.features.auth.flow.StepOutcome
import com.luismejias.lumemedlink.shared.ChileanRut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "¿Olvidaste tu contraseña?" (LumeMed's `PasswordRecoveryViewModel`): a RUT, and the server sends a code.
 *
 * Whatever the server answers about a RUT it does not know, this screen moves on the same way — the server
 * decides whether to reveal that, and the app must not add a second channel that does.
 */
internal class PasswordRecoveryModel(private val gateway: AuthGateway, private val flow: AuthFlowModel) {
    private val current = MutableStateFlow("")
    val rut: StateFlow<String> = current.asStateFlow()

    val canSubmit: Boolean get() = ChileanRut.parse(current.value) != null

    fun rutChanged(value: String) {
        current.value = value
    }

    suspend fun submit(): StepOutcome {
        val rut = ChileanRut.parse(current.value) ?: return StepOutcome.Rejected(AuthCopy.RUT_INVALID)
        return when (val result = gateway.requestPasswordReset(rut)) {
            is AuthResult.Ok -> {
                flow.push(AuthRoute.PASSWORD_RESET_CODE)
                StepOutcome.Advanced
            }
            is AuthResult.Failed -> flow.blockedBy(result.failure)
        }
    }
}

internal data class NewPasswordState(val password: String = "", val repeated: String = "") {
    val mismatch: Boolean get() = repeated.isNotEmpty() && repeated != password
    val canSubmit: Boolean get() = password.isNotEmpty() && password == repeated

    override fun toString(): String = "NewPasswordState(redacted)"
}

/**
 * The new password. The only rule checked here is that both entries match: the password POLICY is the
 * IdP's, and a second copy of it in the app would drift from the first and refuse what the server accepts.
 */
internal class NewPasswordModel(private val gateway: AuthGateway, private val flow: AuthFlowModel) {
    private val current = MutableStateFlow(NewPasswordState())
    val state: StateFlow<NewPasswordState> = current.asStateFlow()

    fun passwordChanged(value: String) {
        current.value = current.value.copy(password = value)
    }

    fun repeatedChanged(value: String) {
        current.value = current.value.copy(repeated = value)
    }

    suspend fun submit(): StepOutcome {
        val typed = current.value
        if (!typed.canSubmit) return StepOutcome.Rejected(AuthCopy.PASSWORDS_DIFFER)
        return when (val result = gateway.setNewPassword(typed.password)) {
            is AuthResult.Ok -> {
                current.value = NewPasswordState()
                flow.endWith(AuthCopy.DONE, AuthCopy.PASSWORD_SAVED)
                StepOutcome.Advanced
            }
            is AuthResult.Failed -> flow.blockedBy(result.failure)
        }
    }
}
