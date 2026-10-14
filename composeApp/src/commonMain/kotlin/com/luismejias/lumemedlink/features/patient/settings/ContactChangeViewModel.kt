package com.luismejias.lumemedlink.features.patient.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.ContactChangeFailure
import com.luismejias.lumemedlink.core.care.ContactChangeGateway
import com.luismejias.lumemedlink.core.care.ContactChangeResult
import com.luismejias.lumemedlink.core.care.ContactCodeDispatch
import com.luismejias.lumemedlink.core.care.ContactKind
import com.luismejias.lumemedlink.core.care.PatientProfile
import com.luismejias.lumemedlink.shared.EmailAddress
import com.luismejias.lumemedlink.shared.PhoneNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The country a new mobile starts on, as the sign-up's does. */
internal const val CONTACT_CHANGE_REGION = "CL"

/** The length of the code a contact change asks for: the app's one length (core/input). */
private const val CONTACT_CODE_LENGTH = 6

/** A change's two steps: the new contact, then the code sent to it. */
internal enum class ContactChangeStep { ENTER, CODE }

/** What went wrong outside the code itself: the change could not be asked for or answered. */
internal enum class ContactChangeTrouble {
    OFFLINE,
    UNAVAILABLE,

    /** Every clinic already holds that contact. */
    UNCHANGED,

    /** Too many codes asked for. */
    LIMIT,

    /** The last sign-in is too old for this change. */
    SIGN_IN_AGAIN,

    /** The change asked for was closed: it starts again from the new contact. */
    CLOSED,
}

/**
 * One contact change, from the pencil to the confirmed code (the author, 2026-10-13: «verificados antes de hacer el
 * cambio por SMS y correo»). Nothing changes on the profile until the code sent to the NEW contact is confirmed, so a
 * typo in the number cannot take the person's account to someone else's phone.
 */
internal data class ContactChangeState(
    val kind: ContactKind,
    /** The profile's contact now: typing it again is refused, there is nothing to change. */
    val current: String,
    val step: ContactChangeStep = ContactChangeStep.ENTER,
    /** The national number's digits, or the address, as typed. */
    val typed: String = "",
    /** The mobile's country, ISO and dial code; Chile until another is picked. Unused for an email. */
    val region: String = CONTACT_CHANGE_REGION,
    val dialCode: String = PhoneNumber.CHILE,
    /** Judged from the first key typed, as the sign-up's fields are. */
    val edited: Boolean = false,
    val sending: Boolean = false,
    val dispatch: ContactCodeDispatch? = null,
    /** The new contact as the code step names it: «+56 9 1234 5678», or the address. */
    val destination: String = "",
    val code: String = "",
    val wrongAttemptsLeft: Int? = null,
    val expired: Boolean = false,
    val trouble: ContactChangeTrouble? = null,
) {
    private val phone: PhoneNumber? get() = PhoneNumber.of(dialCode, typed)
    private val email: EmailAddress? get() = EmailAddress.parse(typed)

    /** The typed value in its canonical form, or `null` while it is not one. */
    private val canonical: String?
        get() = when (kind) {
            ContactKind.PHONE -> phone?.e164
            ContactKind.EMAIL -> email?.value?.lowercase()
        }

    val invalid: Boolean get() = edited && typed.isNotBlank() && canonical == null
    val sameAsCurrent: Boolean get() = canonical != null && canonical == current
    val canSend: Boolean get() = !sending && canonical != null && !sameAsCurrent
    val canConfirm: Boolean get() = !sending && code.length == CONTACT_CODE_LENGTH && wrongAttemptsLeft != 0

    internal val newPhone: PhoneNumber? get() = phone
    internal val newEmail: EmailAddress? get() = email

    override fun toString(): String = "ContactChangeState(redacted)"
}

/**
 * The phone's and the email's change sheets (task 0035), over [ContactChangeGateway]: the platform's verification in a
 * debug build's local environment, the script otherwise (task 0036). The change asked for
 * ([ContactCodeDispatch.change]) lives here, with the sheet, and goes with it.
 */
internal class ContactChangeViewModel(private val care: ContactChangeGateway) : ViewModel() {
    private val current = MutableStateFlow<ContactChangeState?>(null)

    /** The open change, or `null` when no sheet is up. */
    val state: StateFlow<ContactChangeState?> = current.asStateFlow()

    private val landed = MutableStateFlow<ContactKind?>(null)

    /** Which contact just changed, for the host's toast and Configuración's refresh; [doneSeen] once shown. */
    val done: StateFlow<ContactKind?> = landed.asStateFlow()

    fun open(kind: ContactKind, profile: PatientProfile) {
        current.value = ContactChangeState(
            kind = kind,
            current = when (kind) {
                ContactKind.PHONE -> profile.mobile?.e164
                ContactKind.EMAIL -> profile.email?.value?.lowercase()
            }.orEmpty(),
        )
    }

    fun dismiss() {
        current.value = null
    }

    fun doneSeen() {
        landed.value = null
    }

    fun typedChanged(value: String) = edit { copy(typed = value, edited = true, trouble = null) }

    fun regionChanged(region: String, dialCode: String?) = edit { copy(region = region, dialCode = dialCode.orEmpty()) }

    fun codeChanged(value: String) = edit { copy(code = value, wrongAttemptsLeft = null, trouble = null) }

    fun launch(block: suspend ContactChangeViewModel.() -> Unit) {
        viewModelScope.launch { block() }
    }

    /** «Enviar código»: asks for a code to the new contact. */
    suspend fun send() {
        val shown = current.value ?: return
        if (!shown.canSend) return
        edit { copy(sending = true, trouble = null) }
        val result = when (shown.kind) {
            ContactKind.PHONE -> care.requestPhoneChange(requireNotNull(shown.newPhone))
            ContactKind.EMAIL -> care.requestEmailChange(requireNotNull(shown.newEmail))
        }
        when (result) {
            is ContactChangeResult.Ok -> edit {
                copy(
                    sending = false,
                    step = ContactChangeStep.CODE,
                    dispatch = result.value,
                    destination = when (kind) {
                        ContactKind.PHONE -> newPhone?.display.orEmpty()
                        ContactKind.EMAIL -> newEmail?.value.orEmpty()
                    },
                    code = "",
                    expired = false,
                )
            }
            is ContactChangeResult.Failed -> settle(result.failure)
        }
    }

    /** «Reenviar código»: a fresh code, the typed one and any expiry forgotten. */
    suspend fun resend() {
        val change = current.value?.takeIf { it.step == ContactChangeStep.CODE }?.dispatch?.change ?: return
        edit { copy(sending = true, trouble = null) }
        when (val result = care.resendContactCode(change)) {
            is ContactChangeResult.Ok -> edit {
                copy(sending = false, dispatch = result.value, code = "", wrongAttemptsLeft = null, expired = false)
            }
            is ContactChangeResult.Failed -> settle(result.failure)
        }
    }

    /** «Confirmar»: the change lands, the sheet closes, and [done] says which. */
    suspend fun confirm() {
        val shown = current.value ?: return
        val change = shown.dispatch?.change ?: return
        if (!shown.canConfirm) return
        edit { copy(sending = true, trouble = null) }
        when (val result = care.confirmContactChange(change, shown.code)) {
            is ContactChangeResult.Ok -> {
                current.value = null
                landed.value = shown.kind
            }
            is ContactChangeResult.Failed -> settle(result.failure)
        }
    }

    private fun settle(failure: ContactChangeFailure) = edit {
        when (failure) {
            is ContactChangeFailure.WrongCode -> copy(sending = false, wrongAttemptsLeft = failure.attemptsLeft)
            ContactChangeFailure.CodeExpired -> copy(
                sending = false,
                code = "",
                wrongAttemptsLeft = null,
                expired = true,
            )
            ContactChangeFailure.Closed -> copy(
                sending = false,
                step = ContactChangeStep.ENTER,
                dispatch = null,
                code = "",
                wrongAttemptsLeft = null,
                expired = false,
                trouble = ContactChangeTrouble.CLOSED,
            )
            ContactChangeFailure.Unchanged -> copy(sending = false, trouble = ContactChangeTrouble.UNCHANGED)
            ContactChangeFailure.LimitReached -> copy(sending = false, trouble = ContactChangeTrouble.LIMIT)
            ContactChangeFailure.SignInAgain -> copy(sending = false, trouble = ContactChangeTrouble.SIGN_IN_AGAIN)
            ContactChangeFailure.Offline -> copy(sending = false, trouble = ContactChangeTrouble.OFFLINE)
            ContactChangeFailure.Unavailable -> copy(sending = false, trouble = ContactChangeTrouble.UNAVAILABLE)
        }
    }

    private fun edit(change: ContactChangeState.() -> ContactChangeState) {
        current.value = current.value?.change()
    }
}
