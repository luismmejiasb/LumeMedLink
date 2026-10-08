package com.luismejias.lumemedlink.features.auth.totp

import com.luismejias.lumemedlink.core.auth.AuthGateway
import com.luismejias.lumemedlink.core.auth.AuthResult
import com.luismejias.lumemedlink.core.auth.TotpEnrollmentMaterial
import com.luismejias.lumemedlink.features.auth.flow.AuthFlowModel
import com.luismejias.lumemedlink.features.auth.flow.AuthRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Linking an authenticator (LumeMed's `TOTPEnrollmentViewModel`, its ADR-0032): the server mints the secret,
 * the screen shows it as a QR and as a key, and continuing is possible only once it arrived.
 *
 * The secret is in memory only, and it is NOT offered for copying, unlike LumeMed's screen: §8.9 keeps
 * displayed secrets off the shared clipboard, and the key can be typed into the authenticator by hand.
 */
internal class TotpEnrollmentModel(private val gateway: AuthGateway, private val flow: AuthFlowModel) {
    private val current = MutableStateFlow<TotpEnrollmentMaterial?>(null)
    val material: StateFlow<TotpEnrollmentMaterial?> = current.asStateFlow()

    /** Asks the server for the material; a failure becomes the flow's alert, whose retry asks again. */
    suspend fun load() {
        if (current.value != null) return
        when (val result = gateway.beginTotpEnrollment()) {
            is AuthResult.Ok -> current.value = result.value
            is AuthResult.Failed -> flow.blockedBy(result.failure)
        }
    }

    fun proceed() {
        if (current.value != null) flow.push(AuthRoute.TOTP_ENROLLMENT_CONFIRM)
    }
}
