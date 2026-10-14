package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.ContactChangeFailure
import com.luismejias.lumemedlink.core.care.ContactChangeGateway
import com.luismejias.lumemedlink.core.care.ContactChangeResult
import com.luismejias.lumemedlink.core.care.ContactCodeDispatch
import com.luismejias.lumemedlink.core.care.PendingContactChange
import com.luismejias.lumemedlink.core.networking.AppErrorException
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.contract.ConfirmContactVerificationDto
import com.luismejias.lumemedlink.core.networking.contract.ContractOperations
import com.luismejias.lumemedlink.core.networking.contract.RequestContactVerificationDto
import com.luismejias.lumemedlink.core.networking.contract.RequestContactVerificationDtoChannel
import com.luismejias.lumemedlink.core.networking.contract.ResendContactVerificationDto
import com.luismejias.lumemedlink.shared.AppError
import com.luismejias.lumemedlink.shared.EmailAddress
import com.luismejias.lumemedlink.shared.PhoneNumber
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.Instant

private const val TOO_MANY = 429

/**
 * A new phone or e-mail over the contract (task `0036`, the platform's answer to request `0012`, contract 0.131.0):
 * `requestMyContactVerification`, `resendMyContactVerification` and `confirmMyContactVerification`. One code writes the
 * contact in every clinic at once, so the one profile shown stays true (the author, 2026-10-14).
 *
 * Asking demands a sign-in of five minutes or less (the platform's `ADR-0102`). [freshSignIn] runs before each ask: on
 * the local platform it is a fresh dev token, whose `auth_time` is its own; with Identity Platform it will be the
 * re-authentication the screen asks for first (phase B), and until then a stale one reads as
 * [ContactChangeFailure.SignInAgain].
 */
internal class RemoteContactChange(private val contract: ContractClient, private val freshSignIn: suspend () -> Unit) :
    ContactChangeGateway {
    override suspend fun requestPhoneChange(mobile: PhoneNumber): ContactChangeResult<ContactCodeDispatch> =
        request(RequestContactVerificationDtoChannel.PHONE, mobile.e164)

    override suspend fun requestEmailChange(email: EmailAddress): ContactChangeResult<ContactCodeDispatch> =
        request(RequestContactVerificationDtoChannel.EMAIL, email.value)

    override suspend fun resendContactCode(change: PendingContactChange): ContactChangeResult<ContactCodeDispatch> =
        contactCall {
            val sent = contract.call(
                ContractOperations.RESEND_MY_CONTACT_VERIFICATION,
                ResendContactVerificationDto(ticket = change.ticket, value = change.value),
                path = mapOf("verificationId" to change.id),
            )
            ContactCodeDispatch(epochMillis(sent.expiresAt), epochMillis(sent.resendAvailableAt), change)
        }

    /**
     * A `stale_write` is the contact changing elsewhere while this one landed: the platform asks for the same code
     * again, so it is sent once more, once.
     */
    override suspend fun confirmContactChange(change: PendingContactChange, code: String): ContactChangeResult<Unit> =
        contactCall {
            try {
                confirm(change, code)
            } catch (stale: AppErrorException) {
                if (stale.error.problemCode() != "stale_write") throw stale
                confirm(change, code)
            }
        }

    private suspend fun confirm(change: PendingContactChange, code: String) {
        contract.call(
            ContractOperations.CONFIRM_MY_CONTACT_VERIFICATION,
            ConfirmContactVerificationDto(ticket = change.ticket, value = change.value, code = code),
            path = mapOf("verificationId" to change.id),
        )
    }

    private suspend fun request(
        channel: RequestContactVerificationDtoChannel,
        value: String,
    ): ContactChangeResult<ContactCodeDispatch> {
        freshSignIn()
        return contactCall {
            val opened = contract.call(
                ContractOperations.REQUEST_MY_CONTACT_VERIFICATION,
                RequestContactVerificationDto(channel = channel, value = value),
            )
            ContactCodeDispatch(
                epochMillis(opened.expiresAt),
                epochMillis(opened.resendAvailableAt),
                PendingContactChange(opened.id, opened.ticket, value),
            )
        }
    }
}

private suspend fun <T> contactCall(call: suspend () -> T): ContactChangeResult<T> = try {
    ContactChangeResult.Ok(call())
} catch (refused: AppErrorException) {
    currentCoroutineContext().ensureActive()
    ContactChangeResult.Failed(refused.error.contactFailure())
}

/** The platform's refusals (its answer `0077` §3), as the sheet names them. */
internal fun AppError.contactFailure(): ContactChangeFailure = when (problemCode()) {
    "recent_sign_in_required" -> ContactChangeFailure.SignInAgain
    "contact_unchanged" -> ContactChangeFailure.Unchanged
    "contact_verification_expired" -> ContactChangeFailure.CodeExpired
    "contact_verification_closed" -> ContactChangeFailure.Closed
    "contact_verification_code_mismatch" -> (this as? AppError.Validation)?.attemptsLeft
        ?.takeIf { it > 0 }
        ?.let(ContactChangeFailure::WrongCode)
        ?: ContactChangeFailure.Closed
    else -> when {
        // A resend with a ticket or a value that is not this change's, or a change no longer the patient's.
        this is AppError.NotFound -> ContactChangeFailure.Closed
        // Too soon and the day's limit are both 429, and the stack carries no code for either.
        this is AppError.Retryable && status == TOO_MANY -> ContactChangeFailure.LimitReached
        this is AppError.Retryable && status == null -> ContactChangeFailure.Offline
        else -> ContactChangeFailure.Unavailable
    }
}

private fun epochMillis(instant: String): Long = Instant.parse(instant).toEpochMilliseconds()
