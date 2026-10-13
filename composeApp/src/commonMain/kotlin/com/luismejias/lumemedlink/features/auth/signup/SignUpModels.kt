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
import com.luismejias.lumemedlink.features.auth.shared.rutLooksWrong
import com.luismejias.lumemedlink.features.auth.shared.sameRut
import com.luismejias.lumemedlink.shared.ChileanRut
import com.luismejias.lumemedlink.shared.EmailAddress
import com.luismejias.lumemedlink.shared.PhoneNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The 2a fields judged once typed in. The RUT is not among them: it is judged by its length, as on sign-in and
 * recovery ([rutLooksWrong]), and the optional second name and surname never are.
 */
internal enum class SignUpField { FIRST_NAME, FIRST_SURNAME, MOBILE, EMAIL }

/**
 * 2a. Each field is judged on every keystroke from the first one typed in it (the author, 2026-10-13), not on
 * "Continuar": waiting for the button left a whole form to fix at once. A field nobody touched stays quiet until
 * the person tries to continue. While an error shows, "Continuar" is off — the design's disabled button over red
 * fields.
 */
/** The country a sign-up's mobile starts on. */
internal const val CHILE_REGION = "CL"

/** The most a name or surname takes: a real one fits on the field's line (the author, 2026-10-13). */
internal const val NAME_MAX_LENGTH = 40

internal data class SignUpProfileState(
    val rut: String = "",
    val firstName: String = "",
    val secondName: String = "",
    val firstSurname: String = "",
    val secondSurname: String = "",
    /** The national number's digits; its country is [mobileRegion]. */
    val mobile: String = "",
    /** The ISO code of the mobile's country, Chile until the person picks another. */
    val mobileRegion: String = CHILE_REGION,
    /** That country's dial code, «+56». */
    val mobileDialCode: String = PhoneNumber.CHILE,
    val email: String = "",
    val judged: Boolean = false,
    /** The fields typed in at least once: those are judged as they change. */
    val edited: Set<SignUpField> = emptySet(),
    /** 2a-e2. */
    val rutTaken: Boolean = false,
    val offline: Boolean = false,
) {
    private fun judges(field: SignUpField): Boolean = judged || field in edited

    val rutInvalid: Boolean get() = if (judged) ChileanRut.parse(rut) == null else rutLooksWrong(rut)

    /** 2a's ✓: a RUT that passes módulo 11 and is not taken — said as soon as it is typed, not on submit. */
    val rutValid: Boolean get() = !rutTaken && ChileanRut.parse(rut) != null
    val firstNameMissing: Boolean get() = judges(SignUpField.FIRST_NAME) && firstName.isBlank()
    val firstSurnameMissing: Boolean get() = judges(SignUpField.FIRST_SURNAME) && firstSurname.isBlank()
    val mobileInvalid: Boolean get() = judges(SignUpField.MOBILE) && PhoneNumber.of(mobileDialCode, mobile) == null
    val emailInvalid: Boolean get() = judges(SignUpField.EMAIL) && EmailAddress.parse(email) == null

    private val anyError: Boolean
        get() = rutInvalid || firstNameMissing || firstSurnameMissing || mobileInvalid || emailInvalid || rutTaken

    val canSubmit: Boolean get() = !anyError &&
        listOf(rut, firstName, firstSurname, mobile, email).all { it.isNotBlank() }

    /** The typed values as the gateway takes them, or `null` while one is not valid. */
    val profile: SignUpProfile?
        get() {
            val parsedRut = ChileanRut.parse(rut) ?: return null
            val parsedMobile = PhoneNumber.of(mobileDialCode, mobile) ?: return null
            val parsedEmail = EmailAddress.parse(email) ?: return null
            if (firstName.isBlank() || firstSurname.isBlank()) return null
            return SignUpProfile(
                rut = parsedRut,
                firstName = firstName.trim(),
                secondName = secondName.trim().ifEmpty { null },
                firstSurname = firstSurname.trim(),
                secondSurname = secondSurname.trim().ifEmpty { null },
                mobile = parsedMobile,
                email = parsedEmail,
            )
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

    fun firstNameChanged(value: String) = typed(SignUpField.FIRST_NAME) { copy(firstName = value) }

    fun secondNameChanged(value: String) = edit { copy(secondName = value) }

    fun firstSurnameChanged(value: String) = typed(SignUpField.FIRST_SURNAME) { copy(firstSurname = value) }

    fun secondSurnameChanged(value: String) = edit { copy(secondSurname = value) }

    fun mobileChanged(value: String) = typed(SignUpField.MOBILE) { copy(mobile = value) }

    /** Another country for the mobile: its number is judged by that country's rule from now on. */
    fun mobileRegionChanged(region: String, dialCode: String?) = edit {
        copy(mobileRegion = region, mobileDialCode = dialCode.orEmpty())
    }

    fun emailChanged(value: String) = typed(SignUpField.EMAIL) { copy(email = value) }

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

    /** An edit by the person in [field], which from now on is judged as it changes. */
    private fun typed(field: SignUpField, change: SignUpProfileState.() -> SignUpProfileState) = edit {
        change().copy(edited = edited + field)
    }
}

/** 2b. The second consent is the Ley 21.719 authorization: required, and marked so once the rest is ready (2b-e1). */
internal data class SignUpCredentialsState(
    val entry: NewPasswordEntry = NewPasswordEntry(),
    val acceptedTerms: Boolean = false,
    val acceptedDataUse: Boolean = false,
    /** The third consent, optional (ADR-0051 §4): crash reports with a crash-only key. Never blocks «Continuar». */
    val acceptedTelemetry: Boolean = false,
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
    /** Where the optional crash-report consent goes once the step is accepted (ADR-0051 §4). */
    private val telemetryChosen: suspend (Boolean) -> Unit = {},
) {
    private val current = MutableStateFlow(SignUpCredentialsState())
    val state: StateFlow<SignUpCredentialsState> = current.asStateFlow()

    fun passwordChanged(value: String) = edit { copy(entry = entry.copy(password = value)) }

    fun repeatedChanged(value: String) = edit { copy(entry = entry.copy(repeated = value)) }

    fun termsChanged(value: Boolean) = edit { copy(acceptedTerms = value) }

    fun dataUseChanged(value: Boolean) = edit { copy(acceptedDataUse = value) }

    fun telemetryChanged(value: Boolean) = edit { copy(acceptedTelemetry = value) }

    suspend fun submit(): StepOutcome {
        val typed = current.value
        val profile = profileOf() ?: return flow.blocked()
        if (!typed.canSubmit) return ShortRejection
        val credentials = SignUpCredentials(typed.entry.password, typed.acceptedTerms, typed.acceptedDataUse)
        return gateway.submitCredentials(profile, credentials).settle(
            flow,
            onOffline = { edit { copy(offline = true) } },
            onOk = { dispatch ->
                telemetryChosen(typed.acceptedTelemetry)
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
