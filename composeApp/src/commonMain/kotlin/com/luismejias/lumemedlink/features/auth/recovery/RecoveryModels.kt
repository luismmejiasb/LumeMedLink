package com.luismejias.lumemedlink.features.auth.recovery

import com.luismejias.lumemedlink.core.auth.AccessFailure
import com.luismejias.lumemedlink.core.auth.CodeDispatch
import com.luismejias.lumemedlink.core.auth.PatientAccessGateway
import com.luismejias.lumemedlink.features.auth.flow.AccessFlowModel
import com.luismejias.lumemedlink.features.auth.flow.AccessRoute
import com.luismejias.lumemedlink.features.auth.flow.ShortRejection
import com.luismejias.lumemedlink.features.auth.flow.StepOutcome
import com.luismejias.lumemedlink.features.auth.flow.settle
import com.luismejias.lumemedlink.features.auth.shared.CodeEntry
import com.luismejias.lumemedlink.features.auth.shared.NewPasswordEntry
import com.luismejias.lumemedlink.features.auth.shared.rutLooksWrong
import com.luismejias.lumemedlink.features.auth.shared.sameRut
import com.luismejias.lumemedlink.shared.ChileanRut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class RecoveryRutState(val rut: String = "", val offline: Boolean = false) {
    val rutInvalid: Boolean get() = rutLooksWrong(rut)

    val canSubmit: Boolean get() = ChileanRut.parse(rut) != null

    override fun toString(): String = "RecoveryRutState(redacted)"
}

/** 3a. The answer is neutral whatever the RUT: the next screen never says whether an account exists. */
internal class RecoveryRutModel(
    private val gateway: PatientAccessGateway,
    private val flow: AccessFlowModel,
    private val codeSent: (CodeDispatch) -> Unit,
) {
    private val current = MutableStateFlow(RecoveryRutState())
    val state: StateFlow<RecoveryRutState> = current.asStateFlow()

    fun rutChanged(value: String) {
        val now = current.value
        current.value = RecoveryRutState(rut = value, offline = now.offline && sameRut(value, now.rut))
    }

    suspend fun submit(): StepOutcome {
        val rut = ChileanRut.parse(current.value.rut) ?: return ShortRejection
        return gateway.requestPasswordReset(rut).settle(
            flow,
            onOffline = { current.value = current.value.copy(offline = true) },
            onOk = { dispatch ->
                codeSent(dispatch)
                flow.push(AccessRoute.RECOVERY_CODE)
                StepOutcome.Advanced
            },
        )
    }
}

internal data class RecoveryCodeState(val entry: CodeEntry = CodeEntry(), val dispatch: CodeDispatch? = null) {
    override fun toString(): String = "RecoveryCodeState(redacted)"
}

/** 3b, with "Vence en m:ss". A wrong or lapsed code is answered on the same screen; back sends another. */
internal class RecoveryCodeModel(private val gateway: PatientAccessGateway, private val flow: AccessFlowModel) {
    private val current = MutableStateFlow(RecoveryCodeState())
    val state: StateFlow<RecoveryCodeState> = current.asStateFlow()

    fun started(dispatch: CodeDispatch) {
        current.value = RecoveryCodeState(dispatch = dispatch)
    }

    fun codeChanged(value: String) {
        current.value = current.value.copy(entry = CodeEntry(code = value))
    }

    suspend fun submit(): StepOutcome = gateway.verifyResetCode(current.value.entry.code).settle(
        flow,
        onOffline = { setEntry { copy(offline = true) } },
        onOk = {
            flow.push(AccessRoute.RECOVERY_NEW_PASSWORD)
            StepOutcome.Advanced
        },
        onFailure = { failure ->
            when (failure) {
                is AccessFailure.WrongCode -> {
                    setEntry { copy(wrongAttemptsLeft = failure.attemptsLeft) }
                    ShortRejection
                }
                AccessFailure.CodeExpired -> {
                    setEntry { copy(expired = true) }
                    ShortRejection
                }
                else -> null
            }
        },
    )

    private fun setEntry(change: CodeEntry.() -> CodeEntry) {
        current.value = current.value.copy(entry = current.value.entry.change())
    }
}

internal data class NewPasswordState(
    val entry: NewPasswordEntry = NewPasswordEntry(),
    /** The server refused it: it is the password it replaces. */
    val reused: Boolean = false,
    val offline: Boolean = false,
) {
    val canSubmit: Boolean get() = entry.acceptable && !reused

    override fun toString(): String = "NewPasswordState(redacted)"
}

/** 3c, then 3d — which closes the other devices' sessions and sends the person to sign in. */
internal class NewPasswordModel(private val gateway: PatientAccessGateway, private val flow: AccessFlowModel) {
    private val current = MutableStateFlow(NewPasswordState())
    val state: StateFlow<NewPasswordState> = current.asStateFlow()

    fun passwordChanged(value: String) {
        current.value = NewPasswordState(entry = current.value.entry.copy(password = value))
    }

    fun repeatedChanged(value: String) {
        current.value = current.value.copy(entry = current.value.entry.copy(repeated = value), offline = false)
    }

    suspend fun submit(): StepOutcome {
        val typed = current.value
        if (!typed.canSubmit) return ShortRejection
        return gateway.setNewPassword(typed.entry.password).settle(
            flow,
            onOffline = { current.value = current.value.copy(offline = true) },
            onOk = {
                current.value = NewPasswordState()
                flow.replaceWith(AccessRoute.RECOVERY_DONE)
                StepOutcome.Advanced
            },
            onFailure = { failure ->
                if (failure == AccessFailure.PasswordReused) {
                    current.value = typed.copy(reused = true)
                    ShortRejection
                } else {
                    null
                }
            },
        )
    }
}
