package com.luismejias.lumemedlink.features.auth.biometric

import com.luismejias.lumemedlink.core.session.SessionEntryOutcome
import com.luismejias.lumemedlink.core.session.SessionEntryRefusal
import com.luismejias.lumemedlink.core.session.SessionTokens
import com.luismejias.lumemedlink.features.auth.flow.AuthCopy
import com.luismejias.lumemedlink.features.auth.flow.AuthFlowModel
import com.luismejias.lumemedlink.features.auth.flow.StepOutcome

/**
 * The last step: the session begins here, with its biometric material (ADR-0037) — LumeMed's Face ID
 * enrollment screen, with one difference the author decided: **there is no "continue without it"**. No Lume
 * app runs without biometric re-entry; a phone that cannot provide it gets the alert and goes back to the
 * start (the SMS-and-email fallback for phones without the hardware is task 0026, frozen).
 *
 * `establish` is the session entry the composition root hands in — `establishSession` with the real store,
 * gate and lock — so this feature never reaches into `core/session`'s machinery itself. It is passed per
 * call, never kept: the flow outlives a rotation in a ViewModel, and the gate behind `establish` holds the
 * Activity it prompts in.
 */
internal class BiometricEnrollmentModel(private val flow: AuthFlowModel) {
    suspend fun activate(
        establish: suspend (SessionTokens) -> SessionEntryOutcome,
        onEstablished: () -> Unit,
    ): StepOutcome {
        val tokens = flow.takeTokens() ?: return flow.endWith(AuthCopy.FAILED_SHORT, AuthCopy.SESSION_NOT_SAVED)
        return when (val outcome = establish(tokens)) {
            SessionEntryOutcome.Established -> {
                onEstablished()
                StepOutcome.Advanced
            }
            is SessionEntryOutcome.Refused -> when (outcome.reason) {
                SessionEntryRefusal.TIER2_UNAVAILABLE ->
                    flow.endWith(AuthCopy.BIOMETRIC_UNAVAILABLE_TITLE, AuthCopy.BIOMETRIC_UNAVAILABLE_MESSAGE)
                SessionEntryRefusal.SLATE_NOT_CLEARED, SessionEntryRefusal.SESSION_NOT_SAVED ->
                    flow.endWith(AuthCopy.FAILED_SHORT, AuthCopy.SESSION_NOT_SAVED)
            }
        }
    }
}
