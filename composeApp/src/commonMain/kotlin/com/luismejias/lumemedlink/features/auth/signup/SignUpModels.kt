package com.luismejias.lumemedlink.features.auth.signup

import com.luismejias.lumemedlink.core.auth.AccessFailure
import com.luismejias.lumemedlink.core.auth.AccountSummary
import com.luismejias.lumemedlink.core.auth.CodeDispatch
import com.luismejias.lumemedlink.core.auth.PatientAccessGateway
import com.luismejias.lumemedlink.core.auth.SignUpCredentials
import com.luismejias.lumemedlink.core.auth.SignUpProfile
import com.luismejias.lumemedlink.features.auth.flow.AccessFlowModel
import com.luismejias.lumemedlink.features.auth.flow.AccessRoute
import com.luismejias.lumemedlink.features.auth.flow.ShortRejection
import com.luismejias.lumemedlink.features.auth.flow.StepOutcome
import com.luismejias.lumemedlink.features.auth.flow.settle
import com.luismejias.lumemedlink.features.auth.shared.CodeEntry
import com.luismejias.lumemedlink.features.auth.shared.NewPasswordEntry
import com.luismejias.lumemedlink.features.auth.shared.sameRut
import com.luismejias.lumemedlink.shared.ChileanMobile
import com.luismejias.lumemedlink.shared.ChileanRut
import com.luismejias.lumemedlink.shared.EmailAddress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 2a. The errors (2a-e1) appear once the person tries to continue, then follow every keystroke; while one is
 * showing, "Continuar" is off — the design's disabled button over red fields.
 */
internal data class SignUpProfileState(
    val rut: String = "",
    val givenNames: String = "",
    val familyNames: String = "",
    val mobile: String = "",
    val email: String = "",
    val judged: Boolean = false,
    /** 2a-e2. */
    val rutTaken: Boolean = false,
    val offline: Boolean = false,
) {
    val rutInvalid: Boolean get() = judged && ChileanRut.parse(rut) == null

    /** 2a's ✓: a RUT that passes módulo 11 and is not taken — said as soon as it is typed, not on submit. */
    val rutValid: Boolean get() = !rutTaken && ChileanRut.parse(rut) != null
    val givenNamesMissing: Boolean get() = judged && givenNames.isBlank()
    val familyNamesMissing: Boolean get() = judged && familyNames.isBlank()
    val mobileInvalid: Boolean get() = judged && ChileanMobile.parse(mobile) == null
    val emailInvalid: Boolean get() = judged && EmailAddress.parse(email) == null

    private val anyError: Boolean
        get() = rutInvalid || givenNamesMissing || familyNamesMissing || mobileInvalid || emailInvalid || rutTaken

    val canSubmit: Boolean get() = !anyError &&
        listOf(rut, givenNames, familyNames, mobile, email).all { it.isNotBlank() }

    /** The typed values as the gateway takes them, or `null` while one is not valid. */
    val profile: SignUpProfile?
        get() {
            val parsedRut = ChileanRut.parse(rut) ?: return null
            val parsedMobile = ChileanMobile.parse(mobile) ?: return null
            val parsedEmail = EmailAddress.parse(email) ?: return null
            if (givenNames.isBlank() || familyNames.isBlank()) return null
            return SignUpProfile(parsedRut, givenNames.trim(), familyNames.trim(), parsedMobile, parsedEmail)
        }

    override fun toString(): String = "SignUpProfileState(redacted)"
}

internal class SignUpProfileModel(private val gateway: PatientAccessGateway, private val flow: AccessFlowModel) {
    private val current = MutableStateFlow(SignUpProfileState())
    val state: StateFlow<SignUpProfileState> = current.asStateFlow()

    /**
     * The RUT field reformats what it holds ("22222222-2" → "22.222.222-2") and reports that as a change; only a
     * different RUT clears "Ya existe una cuenta", or the refusal would vanish under the person's eyes.
     */
    fun rutChanged(value: String) = edit {
        copy(rut = value, rutTaken = rutTaken && sameRut(value, rut))
    }

    fun givenNamesChanged(value: String) = edit { copy(givenNames = value) }

    fun familyNamesChanged(value: String) = edit { copy(familyNames = value) }

    fun mobileChanged(value: String) = edit { copy(mobile = value) }

    fun emailChanged(value: String) = edit { copy(email = value) }

    suspend fun submit(): StepOutcome {
        current.value = current.value.copy(judged = true)
        val profile = current.value.profile ?: return ShortRejection
        return gateway.checkProfile(profile).settle(
            flow,
            onOffline = { edit { copy(offline = true) } },
            onOk = {
                flow.push(AccessRoute.SIGN_UP_CREDENTIALS)
                StepOutcome.Advanced
            },
            onFailure = { failure ->
                if (failure == AccessFailure.RutTaken) {
                    edit { copy(rutTaken = true) }
                    ShortRejection
                } else {
                    null
                }
            },
        )
    }

    private fun edit(change: SignUpProfileState.() -> SignUpProfileState) {
        current.value = current.value.change().copy(offline = false)
    }
}

/** 2b. The second consent is the Ley 21.719 authorization: required, and marked so once the rest is ready (2b-e1). */
internal data class SignUpCredentialsState(
    val entry: NewPasswordEntry = NewPasswordEntry(),
    val acceptedTerms: Boolean = false,
    val acceptedDataUse: Boolean = false,
    val offline: Boolean = false,
) {
    /** Everything else is right and a consent is still missing: the row says it is required. */
    val consentRequired: Boolean get() = entry.acceptable && !(acceptedTerms && acceptedDataUse)

    val canSubmit: Boolean get() = entry.acceptable && acceptedTerms && acceptedDataUse

    override fun toString(): String = "SignUpCredentialsState(redacted)"
}

internal class SignUpCredentialsModel(
    private val gateway: PatientAccessGateway,
    private val flow: AccessFlowModel,
    private val profileOf: () -> SignUpProfile?,
    private val codeSent: (CodeDispatch, SignUpProfile) -> Unit,
) {
    private val current = MutableStateFlow(SignUpCredentialsState())
    val state: StateFlow<SignUpCredentialsState> = current.asStateFlow()

    fun passwordChanged(value: String) = edit { copy(entry = entry.copy(password = value)) }

    fun repeatedChanged(value: String) = edit { copy(entry = entry.copy(repeated = value)) }

    fun termsChanged(value: Boolean) = edit { copy(acceptedTerms = value) }

    fun dataUseChanged(value: Boolean) = edit { copy(acceptedDataUse = value) }

    suspend fun submit(): StepOutcome {
        val typed = current.value
        val profile = profileOf() ?: return flow.blocked()
        if (!typed.canSubmit) return ShortRejection
        val credentials = SignUpCredentials(typed.entry.password, typed.acceptedTerms, typed.acceptedDataUse)
        return gateway.submitCredentials(profile, credentials).settle(
            flow,
            onOffline = { edit { copy(offline = true) } },
            onOk = { dispatch ->
                codeSent(dispatch, profile)
                flow.push(AccessRoute.SIGN_UP_CODE)
                StepOutcome.Advanced
            },
        )
    }

    private fun edit(change: SignUpCredentialsState.() -> SignUpCredentialsState) {
        current.value = current.value.change().copy(offline = false)
    }
}

internal data class SignUpCodeState(
    val entry: CodeEntry = CodeEntry(),
    val dispatch: CodeDispatch? = null,
    /** "+56 9 •••• 4321" — the step-1 mobile, masked. */
    val maskedMobile: String = "",
) {
    override fun toString(): String = "SignUpCodeState(redacted)"
}

/** 2c, 2c-e1 inline, and the expired screen 2c-e2 with its resend. */
internal class SignUpCodeModel(
    private val gateway: PatientAccessGateway,
    private val flow: AccessFlowModel,
    private val created: (AccountSummary) -> Unit,
) {
    private val current = MutableStateFlow(SignUpCodeState())
    val state: StateFlow<SignUpCodeState> = current.asStateFlow()

    fun started(dispatch: CodeDispatch, profile: SignUpProfile) {
        current.value = SignUpCodeState(dispatch = dispatch, maskedMobile = profile.mobile.masked)
    }

    fun codeChanged(value: String) {
        current.value = current.value.copy(entry = CodeEntry(code = value))
    }

    suspend fun submit(): StepOutcome = gateway.confirmSignUp(current.value.entry.code).settle(
        flow,
        onOffline = { current.value = current.value.copy(entry = current.value.entry.copy(offline = true)) },
        onOk = { summary ->
            created(summary)
            flow.replaceWith(AccessRoute.SIGN_UP_DONE)
            StepOutcome.Advanced
        },
        onFailure = { failure ->
            when (failure) {
                is AccessFailure.WrongCode -> {
                    current.value =
                        current.value.copy(entry = current.value.entry.copy(wrongAttemptsLeft = failure.attemptsLeft))
                    ShortRejection
                }
                AccessFailure.CodeExpired -> {
                    current.value = current.value.copy(entry = CodeEntry())
                    flow.push(AccessRoute.SIGN_UP_CODE_EXPIRED)
                    StepOutcome.Advanced
                }
                else -> null
            }
        },
    )

    /** "Reenviar", on 2c once its interval passed, or "Enviar un código nuevo" on 2c-e2. */
    suspend fun resend(): StepOutcome = gateway.resendSignUpCode().settle(
        flow,
        onOffline = { current.value = current.value.copy(entry = current.value.entry.copy(offline = true)) },
        onOk = { dispatch ->
            current.value = current.value.copy(entry = CodeEntry(), dispatch = dispatch)
            flow.backTo(AccessRoute.SIGN_UP_CODE)
            StepOutcome.Advanced
        },
    )

    /** 2c-e2's "Cambiar el teléfono": back to step 1, where the mobile is. */
    fun changePhone() {
        current.value = SignUpCodeState()
        flow.backTo(AccessRoute.SIGN_UP_PROFILE)
    }
}
