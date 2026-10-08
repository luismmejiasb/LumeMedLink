package com.luismejias.lumemedlink.core.auth

import com.luismejias.lumemedlink.core.session.SessionTokens
import com.luismejias.lumemedlink.shared.ChileanRut

/**
 * Why an auth step did not go through, in the two families the screens treat differently (the
 * LumeMed taxonomy, its ADR-0013): a credential the server REJECTED is answered on the button that sent
 * it; a server that could not be reached is answered with an alert and a retry.
 */
internal sealed interface AuthFailure {
    /** RUT and password do not match. Never says which one — that would tell an attacker the RUT exists. */
    data object InvalidCredentials : AuthFailure

    /** A one-time code the server refused: wrong, expired or already spent. */
    data object InvalidCode : AuthFailure

    /** Too many tries for now; the server decides when it opens again. */
    data object RateLimited : AuthFailure

    /** The server could not be reached, or answered something this app cannot read. */
    data object Unavailable : AuthFailure
}

/** What the server asks for after a correct RUT and password. There is no session yet in either case. */
internal enum class SignInNextStep {
    /** The account has an authenticator: ask for its code. */
    TOTP_CHALLENGE,

    /** The account has none yet: link one (LumeMed's ADR-0032 probe-as-gate, mirrored). */
    TOTP_ENROLLMENT,
}

/**
 * The secret and URI an authenticator app is linked with. Memory only, never stored, never logged:
 * `toString` is redacted because this IS a credential, and a data class prints its fields.
 */
internal class TotpEnrollmentMaterial(val secret: String, val otpauthUri: String) {
    override fun toString(): String = "TotpEnrollmentMaterial(redacted)"
}

internal sealed interface AuthResult<out T> {
    data class Ok<T>(val value: T) : AuthResult<T>

    data class Failed(val failure: AuthFailure) : AuthResult<Nothing>
}

/**
 * The sign-in contract, the seam the auth screens talk to (§3: a ViewModel knows the interface, never
 * the concrete). Every call goes to the IdP or the backend — this app never verifies a password or a
 * code itself (§8.2, ADR-0003).
 *
 * The sequence mirrors LumeMed's flow: RUT + password → a TOTP code (or linking an authenticator first)
 * → the session's tokens. The tokens come back only after the second factor, so no session exists on
 * a password alone.
 */
internal interface AuthGateway {
    suspend fun signIn(rut: ChileanRut, password: String): AuthResult<SignInNextStep>

    suspend fun verifyTotp(code: String): AuthResult<SessionTokens>

    suspend fun beginTotpEnrollment(): AuthResult<TotpEnrollmentMaterial>

    suspend fun confirmTotpEnrollment(code: String): AuthResult<SessionTokens>

    suspend fun requestPasswordReset(rut: ChileanRut): AuthResult<Unit>

    suspend fun verifyPasswordResetCode(code: String): AuthResult<Unit>

    suspend fun setNewPassword(password: String): AuthResult<Unit>
}

/**
 * What is wired today, and it says so (the `UnwiredRefreshClient` precedent): there is no deployed
 * backend and no Identity Platform project for this audience, and the author froze what waits on one
 * (tareas/FREEZE). Every step answers [AuthFailure.Unavailable], so the screens show their real
 * "could not connect" path instead of a sign-in that pretends to work.
 */
internal object UnwiredAuthGateway : AuthGateway {
    private val unavailable = AuthResult.Failed(AuthFailure.Unavailable)

    override suspend fun signIn(rut: ChileanRut, password: String): AuthResult<SignInNextStep> = unavailable

    override suspend fun verifyTotp(code: String): AuthResult<SessionTokens> = unavailable

    override suspend fun beginTotpEnrollment(): AuthResult<TotpEnrollmentMaterial> = unavailable

    override suspend fun confirmTotpEnrollment(code: String): AuthResult<SessionTokens> = unavailable

    override suspend fun requestPasswordReset(rut: ChileanRut): AuthResult<Unit> = unavailable

    override suspend fun verifyPasswordResetCode(code: String): AuthResult<Unit> = unavailable

    override suspend fun setNewPassword(password: String): AuthResult<Unit> = unavailable
}
