package com.luismejias.lumemedlink.features.auth.signin

import com.luismejias.lumemedlink.core.auth.AccessFailure
import com.luismejias.lumemedlink.core.auth.AccountKind
import com.luismejias.lumemedlink.core.auth.PatientAccessGateway
import com.luismejias.lumemedlink.features.auth.flow.AccessFlowModel
import com.luismejias.lumemedlink.features.auth.flow.AccessRole
import com.luismejias.lumemedlink.features.auth.flow.AccessRoute
import com.luismejias.lumemedlink.features.auth.flow.AccessText
import com.luismejias.lumemedlink.features.auth.flow.ShortRejection
import com.luismejias.lumemedlink.features.auth.flow.StepOutcome
import com.luismejias.lumemedlink.features.auth.flow.settle
import com.luismejias.lumemedlink.features.auth.shared.rutLooksWrong
import com.luismejias.lumemedlink.features.auth.shared.sameRut
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.field_rut_invalid
import com.luismejias.lumemedlink.shared.ChileanRut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class SignInState(
    val rut: String = "",
    val password: String = "",
    /** 1b-e1: the last try was wrong, and this many remain before the lockout. */
    val wrongAttemptsLeft: Int? = null,
    /** 1b-e3: the banner, and the button reads "Reintentar". What was typed stays. */
    val offline: Boolean = false,
    /** 1b-e2: when the lockout ends. */
    val lockedUntilEpochMillis: Long? = null,
) {
    val rutInvalid: Boolean get() = rutLooksWrong(rut)

    val canSubmit: Boolean get() = ChileanRut.parse(rut) != null && password.isNotEmpty()

    // A RUT and a password: never printed (§8.1).
    override fun toString(): String = "SignInState(redacted)"
}

/** 1b, and the lockout it can lead to (1b-e2). */
internal class SignInModel(private val gateway: PatientAccessGateway, private val flow: AccessFlowModel) {
    private val current = MutableStateFlow(SignInState())
    val state: StateFlow<SignInState> = current.asStateFlow()

    /** A reformatted RUT is not a new one: the offline banner stays until something really changes. */
    fun rutChanged(value: String) {
        val now = current.value
        current.value = now.copy(rut = value, offline = now.offline && sameRut(value, now.rut))
    }

    fun passwordChanged(value: String) {
        current.value = current.value.copy(password = value, wrongAttemptsLeft = null, offline = false)
    }

    suspend fun submit(): StepOutcome {
        val typed = current.value
        val rut =
            ChileanRut.parse(typed.rut) ?: return StepOutcome.Rejected(AccessText.Plain(Res.string.field_rut_invalid))
        return gateway.signIn(rut, typed.password).settle(
            flow,
            onOffline = { current.value = current.value.copy(offline = true) },
            onOk = { kind ->
                // The password is spent: it leaves memory the moment it is used.
                current.value = SignInState(rut = typed.rut)
                flow.signedInAs = if (kind == AccountKind.DOCTOR) AccessRole.DOCTOR else AccessRole.PATIENT
                flow.push(AccessRoute.QUICK_SIGN_IN)
                StepOutcome.Advanced
            },
            onFailure = { failure ->
                when (failure) {
                    is AccessFailure.InvalidCredentials -> {
                        current.value = typed.copy(wrongAttemptsLeft = failure.attemptsLeft, offline = false)
                        ShortRejection
                    }
                    is AccessFailure.LockedOut -> {
                        current.value = typed.copy(password = "", lockedUntilEpochMillis = failure.untilEpochMillis)
                        flow.push(AccessRoute.LOCKED_OUT)
                        StepOutcome.Advanced
                    }
                    else -> null
                }
            },
        )
    }

    /** From 2a-e2's "Ingresar": the RUT the person already typed comes along. */
    fun prefill(rut: String) {
        current.value = SignInState(rut = rut)
    }
}

internal data class QuickSignInState(val offline: Boolean = false)

/**
 * 1c and 1c-e1. Biometric re-entry is not optional in a Lume app (ADR-0037): there is no "Ahora no", and a phone
 * without a face or fingerprint is sent to its settings or back to the start (ADR-0044).
 */
internal class QuickSignInModel(private val gateway: PatientAccessGateway, private val flow: AccessFlowModel) {
    private val current = MutableStateFlow(QuickSignInState())
    val state: StateFlow<QuickSignInState> = current.asStateFlow()

    suspend fun activate(): StepOutcome = gateway.enableQuickSignIn().settle(
        flow,
        onOffline = { current.value = QuickSignInState(offline = true) },
        onOk = {
            current.value = QuickSignInState()
            flow.finish(flow.signedInAs)
            StepOutcome.Advanced
        },
        onFailure = { failure ->
            if (failure == AccessFailure.BiometricsNotConfigured) {
                flow.push(AccessRoute.QUICK_SIGN_IN_UNAVAILABLE)
                StepOutcome.Advanced
            } else {
                null
            }
        },
    )
}
