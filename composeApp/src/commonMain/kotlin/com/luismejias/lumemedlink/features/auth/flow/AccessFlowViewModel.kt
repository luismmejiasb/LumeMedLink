package com.luismejias.lumemedlink.features.auth.flow

import androidx.lifecycle.ViewModel
import com.luismejias.lumemedlink.core.auth.AccountSummary
import com.luismejias.lumemedlink.core.auth.DoctorLinkGateway
import com.luismejias.lumemedlink.core.auth.PatientAccessGateway
import com.luismejias.lumemedlink.core.crash.CrashConsent
import com.luismejias.lumemedlink.core.session.Clock
import com.luismejias.lumemedlink.features.auth.doctor.DoctorLinkModel
import com.luismejias.lumemedlink.features.auth.recovery.NewPasswordModel
import com.luismejias.lumemedlink.features.auth.recovery.RecoveryCodeModel
import com.luismejias.lumemedlink.features.auth.recovery.RecoveryRutModel
import com.luismejias.lumemedlink.features.auth.signin.QuickSignInModel
import com.luismejias.lumemedlink.features.auth.signin.SignInModel
import com.luismejias.lumemedlink.features.auth.signup.SignUpCodeModel
import com.luismejias.lumemedlink.features.auth.signup.SignUpCredentialsModel
import com.luismejias.lumemedlink.features.auth.signup.SignUpProfileModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One pass through the access flow: the coordinator and every screen's model, wired to the gateways. Thrown
 * away whole on a restart, so nothing typed in one attempt survives into the next (LumeMed's wipe-on-leave).
 * Holds no platform object.
 */
internal class AccessAttempt(
    patients: PatientAccessGateway,
    doctors: DoctorLinkGateway,
    crashReportsChosen: suspend (Boolean) -> Unit = {},
) {
    val flow = AccessFlowModel()
    val signIn = SignInModel(patients, flow)
    val quickSignIn = QuickSignInModel(patients, flow)

    private val account = MutableStateFlow<AccountSummary?>(null)

    /** Who 2d greets. Memory only. */
    val createdAccount: StateFlow<AccountSummary?> = account.asStateFlow()

    val signUpCode = SignUpCodeModel(patients, flow) { account.value = it }
    val signUpProfile = SignUpProfileModel(patients, flow)
    val signUpCredentials = SignUpCredentialsModel(
        patients,
        flow,
        profileOf = { signUpProfile.state.value.profile },
        codeSent = signUpCode::started,
        crashReportsChosen = crashReportsChosen,
    )
    val recoveryCode = RecoveryCodeModel(patients, flow)
    val recoveryRut = RecoveryRutModel(patients, flow, recoveryCode::started)
    val newPassword = NewPasswordModel(patients, flow)
    val doctor = DoctorLinkModel(doctors, flow)
}

/**
 * The access flow's owner (ADR-0045: a `ViewModel` per flow): it outlives a rotation, in memory only, and hands
 * out a fresh [AccessAttempt] whenever the flow restarts. The gateways are chosen by the composition root — today
 * the script of ADR-0044 — and outlive attempts the way a server's memory of failed tries would.
 */
internal class AccessFlowViewModel(
    private val patients: PatientAccessGateway,
    private val doctors: DoctorLinkGateway,
    /** For the countdowns: lockout, code expiry, resend. */
    val clock: Clock,
    /** Where 2b's optional crash-report consent is kept (ADR-0051). */
    private val crashConsent: CrashConsent,
) : ViewModel() {
    private val current = MutableStateFlow(newAttempt())
    val attempt: StateFlow<AccessAttempt> = current.asStateFlow()

    /** Back to the welcome screen with empty forms. */
    fun restart() {
        current.value = newAttempt()
    }

    private fun newAttempt() = AccessAttempt(patients, doctors, crashConsent::setGranted)
}
