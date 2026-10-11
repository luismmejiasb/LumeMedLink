package com.luismejias.lumemedlink.features.auth.doctor

import com.luismejias.lumemedlink.core.auth.AccessFailure
import com.luismejias.lumemedlink.core.auth.DoctorLinkGateway
import com.luismejias.lumemedlink.core.auth.LinkedDoctor
import com.luismejias.lumemedlink.features.auth.flow.AccessFlowModel
import com.luismejias.lumemedlink.features.auth.flow.AccessRole
import com.luismejias.lumemedlink.features.auth.flow.AccessRoute
import com.luismejias.lumemedlink.features.auth.flow.ShortRejection
import com.luismejias.lumemedlink.features.auth.flow.StepOutcome
import com.luismejias.lumemedlink.features.auth.flow.settle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The linking code is eight letters and digits; spaces and the design's "·" are not part of it. */
private const val LINK_CODE_LENGTH = 8

internal data class DoctorCodeState(val typed: String = "", val offline: Boolean = false) {
    /** What the gateway receives: letters and digits only, upper case — accepted however it was typed. */
    val code: String get() = typed.filter { it.isLetterOrDigit() }.uppercase()

    val canSubmit: Boolean get() = code.length == LINK_CODE_LENGTH
}

internal data class DoctorConfirmState(val doctor: LinkedDoctor? = null, val offline: Boolean = false)

/**
 * 4a (the typed code; the QR waits for its ADR), 4a-e1, 4b, 4b-e1 and 4c. The doctor has no account here — only
 * a link to the LumeMed one. No session results (ADR-0044).
 */
internal class DoctorLinkModel(private val gateway: DoctorLinkGateway, private val flow: AccessFlowModel) {
    private val code = MutableStateFlow(DoctorCodeState())
    private val confirm = MutableStateFlow(DoctorConfirmState())
    val codeState: StateFlow<DoctorCodeState> = code.asStateFlow()
    val confirmState: StateFlow<DoctorConfirmState> = confirm.asStateFlow()

    fun codeChanged(value: String) {
        code.value = DoctorCodeState(typed = value)
    }

    suspend fun lookUp(): StepOutcome {
        val typed = code.value
        if (!typed.canSubmit) return ShortRejection
        return gateway.lookUp(typed.code).settle(
            flow,
            onOffline = { code.value = typed.copy(offline = true) },
            onOk = { doctor ->
                confirm.value = DoctorConfirmState(doctor = doctor)
                flow.push(AccessRoute.DOCTOR_CONFIRM)
                StepOutcome.Advanced
            },
            onFailure = { failure ->
                if (failure == AccessFailure.LinkCodeSpent) {
                    flow.push(AccessRoute.DOCTOR_CODE_SPENT)
                    StepOutcome.Advanced
                } else {
                    null
                }
            },
        )
    }

    /** 4a-e1's action: back to the field, empty, for a fresh code. */
    fun typeAnother() {
        code.value = DoctorCodeState()
        flow.backTo(AccessRoute.DOCTOR_CODE)
    }

    /** "Sí, soy yo". */
    suspend fun confirmLink(): StepOutcome = gateway.confirmLink().settle(
        flow,
        onOffline = { confirm.value = confirm.value.copy(offline = true) },
        onOk = {
            flow.replaceWith(AccessRoute.DOCTOR_LINKED)
            StepOutcome.Advanced
        },
        onFailure = { failure ->
            when (failure) {
                AccessFailure.DoctorUnverified -> {
                    flow.replaceWith(AccessRoute.DOCTOR_UNVERIFIED)
                    StepOutcome.Advanced
                }
                AccessFailure.LinkCodeSpent -> {
                    flow.push(AccessRoute.DOCTOR_CODE_SPENT)
                    StepOutcome.Advanced
                }
                else -> null
            }
        },
    )

    /** "No soy yo": the code pointed at someone else's account — drop it and type another. */
    fun notMe() {
        confirm.value = DoctorConfirmState()
        typeAnother()
    }

    /** 4c's "Empezar". */
    fun start() {
        flow.finish(AccessRole.DOCTOR)
    }
}
