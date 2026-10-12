package com.luismejias.lumemedlink.core.auth

import com.luismejias.lumemedlink.core.session.Clock
import com.luismejias.lumemedlink.shared.ChileanRut
import kotlinx.coroutines.delay

/** How long the script pretends the server takes, so the buttons show their spinner as they will on a network. */
private const val SCRIPTED_LATENCY_MILLIS = 600L

/**
 * The synthetic people and codes of the script (task 0029). Fictitious, every one: a real RUT, name or phone
 * here is an incident (§9). The names are the design handoff's own examples.
 */
internal object AccessScript {
    const val PASSWORD = "Clave1234"
    const val OFFLINE_RUT = "11111111-1"
    const val TAKEN_RUT = "22222222-2"
    const val NO_BIOMETRICS_RUT = "33333333-3"
    const val SMS_CODE = "123456"
    const val LINK_CODE = "K7Q2M9XA"
    const val UNVERIFIED_LINK_CODE = "PEND1NG0"
    const val NOT_LINKED_CODE = "S1NENL4C"
    const val DOCTOR_RUT = "12987654-9"
    const val DOCTOR_NAME = "Dra. Mariana Rendón"
    const val DOCTOR_SPECIALTY = "Medicina Familiar"
    const val DOCTOR_PRACTICE = "consulta particular"

    const val SIGN_IN_ATTEMPTS = 5
    const val LOCKOUT_MILLIS = 15 * 60_000L
    const val CODE_ATTEMPTS = 3
    const val CODE_LIFETIME_MILLIS = 10 * 60_000L
    const val RESEND_INTERVAL_MILLIS = 60_000L
}

/**
 * PROVISIONAL — ADR-0044. A script of a contract that DOES NOT EXIST: the design's access flow, played with
 * synthetic data so every screen and edge state can be reached on a device. It is not a mock of the backend,
 * whose contract has another shape, and it must never be mistaken for one.
 *
 * It keeps the state a server would — failed attempts, a lockout, a pending sign-up, a code's expiry — in
 * memory, measured on the injected [clock] (§6). Nothing is written anywhere and no session results.
 */
internal class ScriptedAccessGateway(
    private val clock: Clock,
    private val latency: suspend () -> Unit = { delay(SCRIPTED_LATENCY_MILLIS) },
) : PatientAccessGateway,
    DoctorLinkGateway {
    private var failedSignIns = 0
    private var lockedUntil = 0L
    private var signedIn: ChileanRut? = null
    private var pendingSignUp: SignUpProfile? = null
    private var code: PendingCode? = null
    private var resetVerified = false
    private var linked: Boolean? = null
    private var notLinked = false

    private class PendingCode(val expiresAt: Long, var attemptsLeft: Int)

    override suspend fun signIn(rut: ChileanRut, password: String): AccessResult<Unit> = scripted(rut) {
        val now = clock.nowEpochMillis()
        when {
            now < lockedUntil -> fail(AccessFailure.LockedOut(lockedUntil))
            password == AccessScript.PASSWORD -> {
                failedSignIns = 0
                signedIn = rut
                AccessResult.Ok(Unit)
            }
            else -> {
                failedSignIns += 1
                val left = AccessScript.SIGN_IN_ATTEMPTS - failedSignIns
                if (left > 0) {
                    fail(AccessFailure.InvalidCredentials(left))
                } else {
                    failedSignIns = 0
                    lockedUntil = now + AccessScript.LOCKOUT_MILLIS
                    fail(AccessFailure.LockedOut(lockedUntil))
                }
            }
        }
    }

    override suspend fun enableQuickSignIn(): AccessResult<Unit> = scripted(signedIn) {
        if (signedIn?.canonical == AccessScript.NO_BIOMETRICS_RUT) {
            fail(AccessFailure.BiometricsNotConfigured)
        } else {
            AccessResult.Ok(Unit)
        }
    }

    override suspend fun checkProfile(profile: SignUpProfile): AccessResult<Unit> = scripted(profile.rut) {
        if (profile.rut.canonical == AccessScript.TAKEN_RUT) fail(AccessFailure.RutTaken) else AccessResult.Ok(Unit)
    }

    override suspend fun submitCredentials(
        profile: SignUpProfile,
        credentials: SignUpCredentials,
    ): AccessResult<CodeDispatch> = scripted(profile.rut) {
        pendingSignUp = profile
        AccessResult.Ok(sendCode())
    }

    override suspend fun resendSignUpCode(): AccessResult<CodeDispatch> = scripted(pendingSignUp?.rut) {
        AccessResult.Ok(sendCode())
    }

    override suspend fun confirmSignUp(code: String): AccessResult<AccountSummary> = scripted(pendingSignUp?.rut) {
        when (val verdict = judge(code)) {
            null -> {
                val profile = pendingSignUp ?: return@scripted fail(AccessFailure.Unavailable)
                signedIn = profile.rut
                AccessResult.Ok(AccountSummary(profile.givenNames, profile.familyNames, profile.rut))
            }
            else -> fail(verdict)
        }
    }

    override suspend fun requestPasswordReset(rut: ChileanRut): AccessResult<CodeDispatch> = scripted(rut) {
        // The neutral answer: a code "goes out" whether or not the RUT has an account.
        resetVerified = false
        AccessResult.Ok(sendCode())
    }

    override suspend fun verifyResetCode(code: String): AccessResult<Unit> = scripted(null) {
        when (val verdict = judge(code)) {
            null -> {
                resetVerified = true
                AccessResult.Ok(Unit)
            }
            else -> fail(verdict)
        }
    }

    override suspend fun setNewPassword(password: String): AccessResult<Unit> = scripted(null) {
        when {
            !resetVerified -> fail(AccessFailure.Unavailable)
            password == AccessScript.PASSWORD -> fail(AccessFailure.PasswordReused)
            else -> {
                resetVerified = false
                AccessResult.Ok(Unit)
            }
        }
    }

    override suspend fun lookUp(code: String): AccessResult<LinkedDoctor> = scripted(null) {
        val doctor = ChileanRut.parse(AccessScript.DOCTOR_RUT)
        when {
            doctor == null -> fail(AccessFailure.Unavailable)
            code == AccessScript.LINK_CODE ||
                code == AccessScript.UNVERIFIED_LINK_CODE ||
                code == AccessScript.NOT_LINKED_CODE -> {
                linked = code == AccessScript.LINK_CODE
                notLinked = code == AccessScript.NOT_LINKED_CODE
                AccessResult.Ok(
                    LinkedDoctor(
                        AccessScript.DOCTOR_NAME,
                        AccessScript.DOCTOR_SPECIALTY,
                        AccessScript.DOCTOR_PRACTICE,
                        doctor,
                    ),
                )
            }
            else -> fail(AccessFailure.LinkCodeSpent)
        }
    }

    override suspend fun confirmLink(): AccessResult<Unit> = scripted(null) {
        when {
            notLinked -> fail(AccessFailure.DoctorNotLinked)
            linked == true -> AccessResult.Ok(Unit)
            linked == false -> fail(AccessFailure.DoctorUnverified)
            else -> fail(AccessFailure.LinkCodeSpent)
        }
    }

    /** Every answer waits like a network would, and the offline RUT reaches nothing. */
    private suspend fun <T> scripted(rut: ChileanRut?, answer: () -> AccessResult<T>): AccessResult<T> {
        latency()
        if (rut?.canonical == AccessScript.OFFLINE_RUT) return fail(AccessFailure.Offline)
        return answer()
    }

    private fun sendCode(): CodeDispatch {
        val now = clock.nowEpochMillis()
        code = PendingCode(now + AccessScript.CODE_LIFETIME_MILLIS, AccessScript.CODE_ATTEMPTS)
        return CodeDispatch(now + AccessScript.CODE_LIFETIME_MILLIS, now + AccessScript.RESEND_INTERVAL_MILLIS)
    }

    /** `null` when the code is right; otherwise why not. A spent code stays spent. */
    private fun judge(typed: String): AccessFailure? {
        val pending = code ?: return AccessFailure.CodeExpired
        if (clock.nowEpochMillis() >= pending.expiresAt || pending.attemptsLeft <= 0) return AccessFailure.CodeExpired
        if (typed == AccessScript.SMS_CODE) {
            code = null
            return null
        }
        pending.attemptsLeft -= 1
        return if (pending.attemptsLeft >
            0
        ) {
            AccessFailure.WrongCode(pending.attemptsLeft)
        } else {
            AccessFailure.CodeExpired
        }
    }

    private fun fail(failure: AccessFailure) = AccessResult.Failed(failure)
}
