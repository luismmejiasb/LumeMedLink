package com.luismejias.lumemedlink.core.auth

import com.luismejias.lumemedlink.shared.ChileanRut
import com.luismejias.lumemedlink.shared.EmailAddress
import com.luismejias.lumemedlink.shared.PhoneNumber

/*
 * PROVISIONAL — ADR-0044. These seams have the DESIGN's shape (tareas/PENDING/Handoff, 03-Specs/1-Auth.md),
 * not the backend's. The backend's contract disagrees on purpose: it enrols a patient only through a code the
 * practice issues (T5, `ADR-0031 del backend`), demands TOTP on every request and has no SMS recovery, and it
 * has no doctor-linking operation at all (its task 0117). Which access model wins is the author's open
 * decision. Until then nothing here begins a session: no token, no secure store, no `establishSession`.
 */

/** Why an access step did not go through. Each case is one screen or one message of the design. */
internal sealed interface AccessFailure {
    /** RUT and password do not match — never which one (1b-e1). [attemptsLeft] before the lockout. */
    data class InvalidCredentials(val attemptsLeft: Int) : AccessFailure

    /** Too many failed sign-ins; the server says until when (1b-e2). */
    data class LockedOut(val untilEpochMillis: Long) : AccessFailure

    /** The RUT already has an account (2a-e2). */
    data object RutTaken : AccessFailure

    /** A one-time code that did not match (2c-e1, 3b). [attemptsLeft] before it is spent. */
    data class WrongCode(val attemptsLeft: Int) : AccessFailure

    /** The one-time code outlived its window, or used up its attempts (2c-e2). */
    data object CodeExpired : AccessFailure

    /** The new password is the one it replaces (3c). */
    data object PasswordReused : AccessFailure

    /** The linking code from LumeMed expired or was already used (4a-e1). */
    data object LinkCodeSpent : AccessFailure

    /** The doctor's provider registry is still being verified in LumeMed (4b-e1). */
    data object DoctorUnverified : AccessFailure

    /**
     * The account is not linked to a practice yet: the backend's `not_provisioned` (its ADR-0090). The design has no
     * screen for it; the author added one in the shape of 4b-e1 (2026-10-12).
     */
    data object DoctorNotLinked : AccessFailure

    /** The phone has no Face ID / fingerprint configured (1c-e1). */
    data object BiometricsNotConfigured : AccessFailure

    /** No network: the banner with "Reintentar", and what was typed stays (1b-e3). */
    data object Offline : AccessFailure

    /** The server answered something this app cannot use — never shown as a code (§9 of the spec). */
    data object Unavailable : AccessFailure
}

internal sealed interface AccessResult<out T> {
    data class Ok<T>(val value: T) : AccessResult<T>

    data class Failed(val failure: AccessFailure) : AccessResult<Nothing>
}

/**
 * A one-time code the server just sent. Instants, not durations: the countdowns ("Reenviar en 0:42",
 * "Vence en 9:12") are drawn against the clock, so a code sent before a rotation still expires on time.
 */
internal data class CodeDispatch(val expiresAtEpochMillis: Long, val resendAvailableAtEpochMillis: Long)

/**
 * Step 1 of creating an account (2a). The names in four, as the author asked (2026-10-12): the second name and the
 * second surname are optional — not everyone has them.
 */
internal class SignUpProfile(
    val rut: ChileanRut,
    val firstName: String,
    val secondName: String?,
    val firstSurname: String,
    val secondSurname: String?,
    val mobile: PhoneNumber,
    val email: EmailAddress,
) {
    // Personal data: never printed (§8.1).
    override fun toString(): String = "SignUpProfile(redacted)"
}

/** Step 2 (2b). Both consents are required to get here; they travel so the server can record them. */
internal class SignUpCredentials(val password: String, val acceptedTerms: Boolean, val acceptedDataUse: Boolean) {
    override fun toString(): String = "SignUpCredentials(redacted)"
}

/** Who the new account belongs to, for "¡Listo, Camila!" (2d): the first name and the first surname. */
internal class AccountSummary(val givenNames: String, val familyNames: String, val rut: ChileanRut) {
    override fun toString(): String = "AccountSummary(redacted)"
}

/** The LumeMed account a linking code points to, for "¿Eres tú?" (4b). Display strings, as LumeMed holds them. */
internal class LinkedDoctor(val displayName: String, val specialty: String, val practice: String, val rut: ChileanRut) {
    override fun toString(): String = "LinkedDoctor(redacted)"
}

/**
 * The patient's way in (03-Specs/1-Auth.md §1): sign in with RUT and password, create an account in three
 * steps, or recover the password by SMS. The server decides every rule the design names — attempts, lockout,
 * code lifetime, resend interval — and this app only shows what it answers.
 */
internal interface PatientAccessGateway {
    suspend fun signIn(rut: ChileanRut, password: String): AccessResult<Unit>

    /** Offers Face ID / fingerprint for the next sign-in (1c). */
    suspend fun enableQuickSignIn(): AccessResult<Unit>

    /** Step 1: fails with [AccessFailure.RutTaken] before the person invents a password for nothing. */
    suspend fun checkProfile(profile: SignUpProfile): AccessResult<Unit>

    /** Step 2: sends the SMS to the step-1 mobile. */
    suspend fun submitCredentials(profile: SignUpProfile, credentials: SignUpCredentials): AccessResult<CodeDispatch>

    suspend fun resendSignUpCode(): AccessResult<CodeDispatch>

    /** Step 3: the account exists once this answers. */
    suspend fun confirmSignUp(code: String): AccessResult<AccountSummary>

    /** Answers the same whether or not the RUT has an account (the spec's neutral answer). */
    suspend fun requestPasswordReset(rut: ChileanRut): AccessResult<CodeDispatch>

    suspend fun verifyResetCode(code: String): AccessResult<Unit>

    /** Closes the sessions on other devices (3d). */
    suspend fun setNewPassword(password: String): AccessResult<Unit>
}

/**
 * The doctor's way in (03-Specs/1-Auth.md §2): no account here, only a link to the one in LumeMed, by an
 * eight-character code shown there. The QR path waits for its own ADR (camera, no GMS).
 */
internal interface DoctorLinkGateway {
    suspend fun lookUp(code: String): AccessResult<LinkedDoctor>

    /** "Sí, soy yo" (4b → 4c). */
    suspend fun confirmLink(): AccessResult<Unit>
}
