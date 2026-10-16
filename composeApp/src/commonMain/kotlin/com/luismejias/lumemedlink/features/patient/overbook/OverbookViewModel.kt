package com.luismejias.lumemedlink.features.patient.overbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.DirectoryDoctor
import com.luismejias.lumemedlink.core.care.OverbookDraft
import com.luismejias.lumemedlink.core.care.OverbookFailure
import com.luismejias.lumemedlink.core.care.OverbookReason
import com.luismejias.lumemedlink.core.care.OverbookRequests
import com.luismejias.lumemedlink.core.care.OverbookResult
import com.luismejias.lumemedlink.core.care.OverbookUrgency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The sheet that asks a practice for an overbook (ADR-0069): the professional, how urgent and why, both from closed
 * lists. [clientId] names this attempt: a retry sends it again, and choosing something else renews it.
 */
internal data class OverbookAsk(
    val doctor: DirectoryDoctor,
    val clientId: String,
    val urgency: OverbookUrgency? = null,
    val reason: OverbookReason? = null,
    val failure: OverbookFailure? = null,
) {
    val draft: OverbookDraft?
        get() = if (urgency != null && reason != null) OverbookDraft(doctor.doctor.id, urgency, reason) else null

    // Never the reason or the urgency (ADR-0069 point 2).
    override fun toString(): String = "OverbookAsk(ready=${draft != null}, failure=${failure != null})"
}

/** The sheet's owner, opened from a result whose professional takes requests. */
@OptIn(ExperimentalUuidApi::class)
internal class OverbookViewModel(private val requests: OverbookRequests) : ViewModel() {
    private val current = MutableStateFlow<OverbookAsk?>(null)
    val state: StateFlow<OverbookAsk?> = current.asStateFlow()

    private val sentFlag = MutableStateFlow(false)

    /** True once a request went through, until the host has said so. */
    val sent: StateFlow<Boolean> = sentFlag.asStateFlow()

    fun open(doctor: DirectoryDoctor) {
        current.value = OverbookAsk(doctor, newKey())
    }

    fun urgencyChosen(urgency: OverbookUrgency) = choose { copy(urgency = urgency) }

    fun reasonChosen(reason: OverbookReason) = choose { copy(reason = reason) }

    fun launchSend() {
        viewModelScope.launch { send() }
    }

    suspend fun send() {
        val asking = current.value ?: return
        val draft = asking.draft ?: return
        update { copy(failure = null) }
        val result = requests.requestOverbook(draft, asking.clientId)
        // The answer is this attempt's: a sheet closed and opened again meanwhile keeps its own state.
        if (current.value?.clientId != asking.clientId) {
            if (result is OverbookResult.Done) sentFlag.value = true
            return
        }
        when (result) {
            is OverbookResult.Done -> {
                current.value = null
                sentFlag.value = true
            }
            is OverbookResult.Failed -> update { copy(failure = result.failure) }
        }
    }

    fun dismiss() {
        current.value = null
    }

    fun sentSeen() {
        sentFlag.value = false
    }

    /** What is sent changed: a new attempt, with a new key (ADR-0069 point 3). */
    private fun choose(change: OverbookAsk.() -> OverbookAsk) = update {
        val next = change()
        if (next.draft != draft) next.copy(clientId = newKey(), failure = null) else next
    }

    private fun update(change: OverbookAsk.() -> OverbookAsk) {
        current.value = current.value?.change()
    }

    private fun newKey(): String = Uuid.random().toString()
}
