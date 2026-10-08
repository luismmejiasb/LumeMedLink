package com.luismejias.lumemedlink.features.auth

import com.luismejias.lumemedlink.core.auth.AuthFailure
import com.luismejias.lumemedlink.core.auth.AuthGateway
import com.luismejias.lumemedlink.core.auth.AuthResult
import com.luismejias.lumemedlink.core.auth.SignInNextStep
import com.luismejias.lumemedlink.core.auth.TotpEnrollmentMaterial
import com.luismejias.lumemedlink.core.auth.UnwiredAuthGateway
import com.luismejias.lumemedlink.core.session.LockOutcome
import com.luismejias.lumemedlink.core.session.SessionEndReason
import com.luismejias.lumemedlink.core.session.SessionEntryOutcome
import com.luismejias.lumemedlink.core.session.SessionEntryRefusal
import com.luismejias.lumemedlink.core.session.SessionTokens
import com.luismejias.lumemedlink.features.auth.flow.AuthCopy
import com.luismejias.lumemedlink.features.auth.flow.AuthFlowSession
import com.luismejias.lumemedlink.features.auth.flow.AuthRoute
import com.luismejias.lumemedlink.features.auth.flow.StepOutcome
import com.luismejias.lumemedlink.features.auth.unlock.stepOutcomeFor
import com.luismejias.lumemedlink.shared.ChileanRut
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Synthetic only (§9): a RUT valid by módulo 11 that belongs to nobody known, a password and codes made up.
private const val RUT = "11.111.111-1"
private const val PASSWORD = "synthetic-password"
private val TOKENS = SessionTokens("acc-synthetic", "ref-synthetic", Long.MAX_VALUE)

/** A gateway whose answers a test scripts, and which remembers what it was asked. */
private class ScriptedGateway(
    var signIn: AuthResult<SignInNextStep> = AuthResult.Ok(SignInNextStep.TOTP_CHALLENGE),
    var totp: AuthResult<SessionTokens> = AuthResult.Ok(TOKENS),
    var enrollment: AuthResult<TotpEnrollmentMaterial> = AuthResult.Ok(
        TotpEnrollmentMaterial("SYNTHETICKEY", "otpauth://totp/synthetic"),
    ),
    var reset: AuthResult<Unit> = AuthResult.Ok(Unit),
) : AuthGateway {
    val asked = mutableListOf<String>()

    override suspend fun signIn(rut: ChileanRut, password: String): AuthResult<SignInNextStep> {
        asked += "signIn:${rut.canonical}"
        return signIn
    }

    override suspend fun verifyTotp(code: String) = totp.also { asked += "verifyTotp:$code" }

    override suspend fun beginTotpEnrollment() = enrollment.also { asked += "beginTotpEnrollment" }

    override suspend fun confirmTotpEnrollment(code: String) = totp.also { asked += "confirmTotp:$code" }

    override suspend fun requestPasswordReset(rut: ChileanRut) = reset.also { asked += "reset:${rut.canonical}" }

    override suspend fun verifyPasswordResetCode(code: String) = reset.also { asked += "resetCode:$code" }

    override suspend fun setNewPassword(password: String) = reset.also { asked += "newPassword" }
}

private suspend fun AuthFlowSession.signInWith(rut: String = RUT, password: String = PASSWORD): StepOutcome {
    login.rutChanged(rut)
    login.passwordChanged(password)
    return login.submit()
}

/**
 * The sign-in flow copied from LumeMed, pinned where it decides: which screen follows which, what a refused
 * credential and an unreachable server each produce, where back is allowed, and that no session exists
 * until the biometric material is made (ADR-0037).
 */
class AuthFlowTest {

    @Test
    fun theFlowOfAnAccountWithAnAuthenticator() = runTest {
        val gateway = ScriptedGateway()
        val auth = AuthFlowSession(gateway)

        assertEquals(StepOutcome.Advanced, auth.signInWith())
        assertEquals(AuthRoute.MFA_CHALLENGE, auth.flow.top)
        assertEquals("", auth.login.state.value.password, "the password stays in memory after it was spent")

        auth.mfa.codeChanged("123456")
        assertEquals(StepOutcome.Advanced, auth.mfa.submit())
        assertEquals(AuthRoute.BIOMETRIC_ENROLLMENT, auth.flow.top)
        assertEquals(TOKENS, auth.flow.pendingTokens)

        var established = false
        val outcome = auth.biometric.activate({ SessionEntryOutcome.Established }) { established = true }
        assertEquals(StepOutcome.Advanced, outcome)
        assertTrue(established)
        assertNull(auth.flow.pendingTokens, "the tokens stay in the flow after the session took them")
    }

    @Test
    fun anAccountWithoutAnAuthenticatorLinksOneFirst() = runTest {
        val auth = AuthFlowSession(ScriptedGateway(signIn = AuthResult.Ok(SignInNextStep.TOTP_ENROLLMENT)))

        auth.signInWith()
        assertEquals(AuthRoute.TOTP_ENROLLMENT, auth.flow.top)
        auth.totpEnrollment.proceed()
        assertEquals(AuthRoute.TOTP_ENROLLMENT, auth.flow.top, "continued before the material arrived")

        auth.totpEnrollment.load()
        auth.totpEnrollment.proceed()
        assertEquals(AuthRoute.TOTP_ENROLLMENT_CONFIRM, auth.flow.top)

        auth.totpConfirm.codeChanged("654321")
        auth.totpConfirm.submit()
        assertEquals(AuthRoute.BIOMETRIC_ENROLLMENT, auth.flow.top)
    }

    @Test
    fun aRefusedCredentialIsAnsweredOnTheButtonAlone() = runTest {
        val auth = AuthFlowSession(ScriptedGateway(signIn = AuthResult.Failed(AuthFailure.InvalidCredentials)))

        assertEquals(StepOutcome.Rejected(AuthCopy.WRONG_CREDENTIALS), auth.signInWith())
        assertEquals(AuthRoute.LOGIN, auth.flow.top)
        assertNull(auth.flow.alert.value, "a refusal is not an alert")
    }

    @Test
    fun anInvalidRutNeverReachesTheServer() = runTest {
        val gateway = ScriptedGateway()
        val auth = AuthFlowSession(gateway)

        assertEquals(StepOutcome.Rejected(AuthCopy.RUT_INVALID), auth.signInWith(rut = "11.111.111-2"))
        assertTrue(gateway.asked.isEmpty())
        assertTrue(auth.login.state.value.rutInvalid)
        assertFalse(auth.login.state.value.canSubmit)
    }

    @Test
    fun anUnreachableServerIsTheFlowsAlertAndItsRetryReRunsTheStep() = runTest {
        val auth = AuthFlowSession(UnwiredAuthGateway)

        assertEquals(StepOutcome.Blocked, auth.signInWith())
        val alert = auth.flow.alert.value
        assertEquals(AuthCopy.UNAVAILABLE_TITLE, alert?.title)
        assertTrue(alert?.retryable == true)

        val before = auth.flow.retryEpoch.value
        auth.flow.alertConfirmed()
        assertEquals(before + 1, auth.flow.retryEpoch.value, "the retry did not re-run the step")
        assertNull(auth.flow.alert.value)
    }

    @Test
    fun aWrongCodeIsClearedForTheNextTry() = runTest {
        val auth = AuthFlowSession(ScriptedGateway(totp = AuthResult.Failed(AuthFailure.InvalidCode)))
        auth.signInWith()

        auth.mfa.codeChanged("12a34 56")
        assertEquals("123456", auth.mfa.code.value, "only digits, at most six")
        assertEquals(StepOutcome.Rejected(AuthCopy.WRONG_CODE), auth.mfa.submit())
        assertEquals("", auth.mfa.code.value)
        assertEquals(AuthRoute.MFA_CHALLENGE, auth.flow.top)
    }

    @Test
    fun spentStepsHaveNoWayBack() = runTest {
        val auth = AuthFlowSession(ScriptedGateway())
        auth.signInWith()

        assertFalse(auth.flow.back(), "back from the code to a password already spent")
        assertEquals(AuthRoute.MFA_CHALLENGE, auth.flow.top)
        AuthRoute.entries.forEach { route ->
            val expected = route in setOf(
                AuthRoute.TOTP_ENROLLMENT_CONFIRM,
                AuthRoute.PASSWORD_RECOVERY,
                AuthRoute.PASSWORD_RESET_CODE,
            )
            assertEquals(expected, route.allowsBack, "$route")
        }
    }

    @Test
    fun noBiometricsEndsTheAttemptAndStartsOverEmpty() = runTest {
        val auth = AuthFlowSession(ScriptedGateway())
        auth.signInWith()
        auth.mfa.codeChanged("123456")
        auth.mfa.submit()

        val outcome = auth.biometric.activate({ SessionEntryOutcome.Refused(SessionEntryRefusal.TIER2_UNAVAILABLE) }) {
            error("a session began without biometric material")
        }

        assertEquals(StepOutcome.Blocked, outcome)
        assertEquals(AuthCopy.BIOMETRIC_UNAVAILABLE_TITLE, auth.flow.alert.value?.title)
        assertNull(auth.flow.pendingTokens, "the tokens of a refused session stayed in memory")
        val restartsBefore = auth.flow.restarts.value
        auth.flow.alertConfirmed()
        assertEquals(AuthRoute.LOGIN, auth.flow.top, "no way past the biometric step without it (author, 2026-10-07)")
        assertEquals(restartsBefore + 1, auth.flow.restarts.value, "the owner is not told to start an empty attempt")
    }

    @Test
    fun thePasswordResetFlow() = runTest {
        val gateway = ScriptedGateway()
        val auth = AuthFlowSession(gateway)

        auth.flow.push(AuthRoute.PASSWORD_RECOVERY)
        auth.recovery.rutChanged(RUT)
        assertEquals(StepOutcome.Advanced, auth.recovery.submit())
        assertEquals(AuthRoute.PASSWORD_RESET_CODE, auth.flow.top)

        auth.resetCode.codeChanged("111111")
        auth.resetCode.submit()
        assertEquals(AuthRoute.PASSWORD_RESET_NEW_PASSWORD, auth.flow.top)

        auth.newPassword.passwordChanged("synthetic-new")
        auth.newPassword.repeatedChanged("synthetic-other")
        assertTrue(auth.newPassword.state.value.mismatch)
        assertEquals(StepOutcome.Rejected(AuthCopy.PASSWORDS_DIFFER), auth.newPassword.submit())
        assertFalse("newPassword" in gateway.asked, "a mismatch reached the server")

        auth.newPassword.repeatedChanged("synthetic-new")
        auth.newPassword.submit()
        assertEquals(AuthCopy.PASSWORD_SAVED, auth.flow.alert.value?.message)
        auth.flow.alertConfirmed()
        assertEquals(AuthRoute.LOGIN, auth.flow.top)
    }

    @Test
    fun theLockScreenShowsAttemptsLeftAndNeverFlashesAFailureForADismissal() {
        assertEquals(StepOutcome.Advanced, stepOutcomeFor(LockOutcome.Unlocked))
        assertEquals(StepOutcome.Rejected(AuthCopy.attemptsLeft(2)), stepOutcomeFor(LockOutcome.StillLocked(2)))
        assertEquals(StepOutcome.Retry(AuthCopy.UNLOCK_TRY_AGAIN), stepOutcomeFor(LockOutcome.StillLocked(null)))
        assertEquals(StepOutcome.Blocked, stepOutcomeFor(LockOutcome.SessionEnded(SessionEndReason.TOO_MANY_ATTEMPTS)))
    }

    @Test
    fun nothingTypedIsPrinted() {
        val auth = AuthFlowSession(UnwiredAuthGateway)
        auth.login.rutChanged(RUT)
        auth.login.passwordChanged(PASSWORD)
        val printed = auth.login.state.value.toString()
        assertFalse("1111" in printed || PASSWORD in printed, printed)
        assertFalse("SYNTHETICKEY" in TotpEnrollmentMaterial("SYNTHETICKEY", "otpauth://x").toString())
    }
}
