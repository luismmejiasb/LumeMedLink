package com.luismejias.lumemedlink.features.auth.flow

import com.luismejias.lumemedlink.core.auth.AuthGateway
import com.luismejias.lumemedlink.core.session.SessionTokens
import com.luismejias.lumemedlink.features.auth.biometric.BiometricEnrollmentModel
import com.luismejias.lumemedlink.features.auth.login.LoginModel
import com.luismejias.lumemedlink.features.auth.mfa.CodeStepModel
import com.luismejias.lumemedlink.features.auth.recovery.NewPasswordModel
import com.luismejias.lumemedlink.features.auth.recovery.PasswordRecoveryModel
import com.luismejias.lumemedlink.features.auth.totp.TotpEnrollmentModel

/**
 * One sign-in attempt: the coordinator and every screen's model, wired to one gateway. The composition
 * root keeps it in the shell's ViewModel so a rotation does not restart the flow — in memory only — and
 * throws it away when a session begins or the flow restarts, which is LumeMed's wipe-on-leave in one move.
 *
 * Holds no platform object, so keeping it across a configuration change leaks nothing.
 */
internal class AuthFlowSession(gateway: AuthGateway) {
    val flow = AuthFlowModel()
    val login = LoginModel(gateway, flow)
    val mfa = CodeStepModel<SessionTokens>(flow, gateway::verifyTotp, flow::tokensReceived)
    val totpEnrollment = TotpEnrollmentModel(gateway, flow)
    val totpConfirm = CodeStepModel<SessionTokens>(flow, gateway::confirmTotpEnrollment, flow::tokensReceived)
    val biometric = BiometricEnrollmentModel(flow)
    val recovery = PasswordRecoveryModel(gateway, flow)
    val resetCode = CodeStepModel<Unit>(flow, gateway::verifyPasswordResetCode) {
        flow.push(AuthRoute.PASSWORD_RESET_NEW_PASSWORD)
    }
    val newPassword = NewPasswordModel(gateway, flow)
}
