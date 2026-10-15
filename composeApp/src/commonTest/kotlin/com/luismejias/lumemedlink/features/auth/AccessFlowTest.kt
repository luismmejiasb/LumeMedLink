package com.luismejias.lumemedlink.features.auth

import com.luismejias.lumemedlink.core.auth.AccessScript
import com.luismejias.lumemedlink.core.auth.ManualClock
import com.luismejias.lumemedlink.core.auth.ScriptedAccessGateway
import com.luismejias.lumemedlink.core.session.LockOutcome
import com.luismejias.lumemedlink.core.session.SessionEndReason
import com.luismejias.lumemedlink.features.auth.flow.AccessAttempt
import com.luismejias.lumemedlink.features.auth.flow.AccessFlowModel
import com.luismejias.lumemedlink.features.auth.flow.AccessRole
import com.luismejias.lumemedlink.features.auth.flow.AccessRoute
import com.luismejias.lumemedlink.features.auth.flow.AccessText
import com.luismejias.lumemedlink.features.auth.flow.OfflineOutcome
import com.luismejias.lumemedlink.features.auth.flow.ShortRejection
import com.luismejias.lumemedlink.features.auth.flow.StepOutcome
import com.luismejias.lumemedlink.features.auth.shared.PasswordRules
import com.luismejias.lumemedlink.features.auth.signup.SignUpProfileState
import com.luismejias.lumemedlink.features.auth.unlock.stepOutcomeFor
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.unlock_attempts_left
import com.luismejias.lumemedlink.resources.unlock_try_again
import com.luismejias.lumemedlink.shared.PhoneNumber
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Synthetic only (§9): RUTs valid by módulo 11 that belong to nobody known; the script's own codes.
private const val RUT = "18.456.789-K"

/** The access flow end to end against the script, with a hand-moved clock and no latency. */
class AccessFlowTest {
    private val clock = ManualClock()
    private val gateway = ScriptedAccessGateway(clock, latency = {})
    private val attempt = AccessAttempt(gateway)
    private val flow = attempt.flow

    private fun path(vararg routes: AccessRoute) = assertEquals(routes.toList(), flow.routes.value)

    @Test
    fun theBackPolicyIsTheDesignsAndNeverLeavesTheStart() {
        val model = AccessFlowModel()
        assertFalse(model.back())
        model.push(AccessRoute.SIGN_IN)
        model.push(AccessRoute.QUICK_SIGN_IN)
        assertFalse(model.back(), "the password behind 1c is spent")
        assertEquals(AccessRoute.QUICK_SIGN_IN, model.top)
    }

    @Test
    fun signingInGoesToQuickSignInAndThenIn() = runTest {
        flow.push(AccessRoute.SIGN_IN)
        attempt.signIn.rutChanged(RUT)
        attempt.signIn.passwordChanged(AccessScript.PASSWORD)
        assertEquals(StepOutcome.Advanced, attempt.signIn.submit())
        path(AccessRoute.WELCOME, AccessRoute.SIGN_IN, AccessRoute.QUICK_SIGN_IN)
        assertEquals("", attempt.signIn.state.value.password, "the password leaves memory once used")

        assertEquals(StepOutcome.Advanced, attempt.quickSignIn.activate())
        assertEquals(AccessRole.PATIENT, flow.finished.value)
    }

    @Test
    fun aWrongPasswordStaysOnTheScreenAndTheFifthLocks() = runTest {
        flow.push(AccessRoute.SIGN_IN)
        attempt.signIn.rutChanged(RUT)
        repeat(4) { i ->
            attempt.signIn.passwordChanged("nope")
            attempt.signIn.submit()
            assertEquals(4 - i, attempt.signIn.state.value.wrongAttemptsLeft)
        }
        attempt.signIn.passwordChanged("nope")
        attempt.signIn.submit()
        assertEquals(AccessRoute.LOCKED_OUT, flow.top)
        assertEquals(clock.now + AccessScript.LOCKOUT_MILLIS, attempt.signIn.state.value.lockedUntilEpochMillis)
        assertTrue(flow.backTo(AccessRoute.SIGN_IN))
    }

    @Test
    fun noNetworkKeepsWhatWasTypedAndShowsTheBanner() = runTest {
        flow.push(AccessRoute.SIGN_IN)
        attempt.signIn.rutChanged(AccessScript.OFFLINE_RUT)
        attempt.signIn.passwordChanged("anything")
        assertEquals(OfflineOutcome, attempt.signIn.submit())
        val state = attempt.signIn.state.value
        assertTrue(state.offline)
        assertEquals("anything", state.password)
        assertEquals(AccessRoute.SIGN_IN, flow.top)
        attempt.signIn.rutChanged("11.111.111-1")
        assertTrue(attempt.signIn.state.value.offline, "a reformatted RUT keeps the banner")
    }

    @Test
    fun aPhoneWithoutBiometricsIsSentToItsSettingsNotIn() = runTest {
        flow.push(AccessRoute.SIGN_IN)
        attempt.signIn.rutChanged(AccessScript.NO_BIOMETRICS_RUT)
        attempt.signIn.passwordChanged(AccessScript.PASSWORD)
        attempt.signIn.submit()
        attempt.quickSignIn.activate()
        assertEquals(AccessRoute.QUICK_SIGN_IN_UNAVAILABLE, flow.top)
        assertNull(flow.finished.value)
    }

    @Test
    fun creatingAnAccountRunsTheThreeStepsAndGreetsByName() = runTest {
        flow.push(AccessRoute.SIGN_UP_PROFILE)
        val profile = attempt.signUpProfile
        profile.rutChanged(RUT)
        profile.firstNameChanged("Camila")
        profile.firstSurnameChanged("Soto")
        profile.mobileChanged("9 8765 4321")
        profile.emailChanged("camila.soto@correo.cl")
        assertEquals(StepOutcome.Advanced, profile.submit())

        val credentials = attempt.signUpCredentials
        credentials.passwordChanged("Synthetic1!")
        credentials.repeatedChanged("Synthetic1!")
        assertFalse(credentials.state.value.consentRequired, "nothing is said before «Continuar» is pressed")
        assertTrue(credentials.state.value.canContinue)
        assertEquals(ShortRejection, credentials.submit())
        assertTrue(credentials.state.value.consentRequired, "the missing consents are named once continuing was tried")
        credentials.termsChanged(true)
        credentials.dataUseChanged(true)
        assertEquals(StepOutcome.Advanced, credentials.submit())
        assertEquals("+56 9 •••• 4321".replace(" ", PhoneNumber.NO_BREAK), attempt.signUpCode.state.value.maskedMobile)

        attempt.signUpCode.codeChanged("000000")
        attempt.signUpCode.submit()
        assertEquals(2, attempt.signUpCode.state.value.entry.wrongAttemptsLeft)
        attempt.signUpCode.codeChanged(AccessScript.SMS_CODE)
        assertEquals(StepOutcome.Advanced, attempt.signUpCode.submit())
        path(AccessRoute.SIGN_UP_DONE)
        assertEquals("Camila", attempt.createdAccount.value?.givenNames)
    }

    @Test
    fun stepOneJudgesEachFieldAsItIsTypedAndATakenRutOffersTheWayIn() = runTest {
        val profile = attempt.signUpProfile
        val untouched = profile.state.value
        assertFalse(
            untouched.rutInvalid || untouched.firstNameMissing || untouched.mobileInvalid || untouched.emailInvalid,
            "an empty form is not red",
        )
        profile.rutChanged("18.4")
        assertFalse(profile.state.value.rutInvalid, "a RUT is not judged before it could be whole")
        profile.rutChanged("18.456.789-5")
        profile.mobileChanged("+56 9 8765 43")
        profile.emailChanged("camila.soto@correo")
        val typed = profile.state.value
        assertTrue(typed.rutInvalid && typed.mobileInvalid && typed.emailInvalid, "red as typed, not on Continuar")
        assertFalse(typed.firstNameMissing, "a field nobody typed in stays quiet")
        profile.firstNameChanged("Camila")
        profile.firstNameChanged("")
        assertTrue(profile.state.value.firstNameMissing, "a name typed and erased is missing at once")
        profile.firstNameChanged("Camila")
        profile.firstSurnameChanged("Soto")
        assertFalse(profile.state.value.canSubmit)

        profile.rutChanged(AccessScript.TAKEN_RUT)
        profile.mobileChanged("+56 9 8765 4321")
        profile.emailChanged("camila.soto@correo.cl")
        profile.submit()
        assertTrue(profile.state.value.rutTaken)
        // The field reformats the RUT it holds; that is not a new RUT, and the refusal stays.
        profile.rutChanged("22.222.222-2")
        assertTrue(profile.state.value.rutTaken)
        profile.rutChanged(RUT)
        assertFalse(profile.state.value.rutTaken)
    }

    @Test
    fun anExpiredCodeOffersANewOneOrAnotherPhone() = runTest {
        flow.push(AccessRoute.SIGN_UP_PROFILE)
        attempt.signUpProfile.apply {
            rutChanged(RUT)
            firstNameChanged("Camila")
            firstSurnameChanged("Soto")
            mobileChanged("987654321")
            emailChanged("camila.soto@correo.cl")
            submit()
        }
        attempt.signUpCredentials.apply {
            passwordChanged("Synthetic1!")
            repeatedChanged("Synthetic1!")
            termsChanged(true)
            dataUseChanged(true)
            submit()
        }
        clock.now += AccessScript.CODE_LIFETIME_MILLIS
        attempt.signUpCode.codeChanged(AccessScript.SMS_CODE)
        attempt.signUpCode.submit()
        assertEquals(AccessRoute.SIGN_UP_CODE_EXPIRED, flow.top)
        attempt.signUpCode.resend()
        assertEquals(AccessRoute.SIGN_UP_CODE, flow.top)
        flow.push(AccessRoute.SIGN_UP_CODE_EXPIRED)
        attempt.signUpCode.changePhone()
        assertEquals(AccessRoute.SIGN_UP_PROFILE, flow.top)
    }

    @Test
    fun recoveryEndsAtSignInAndCannotReuseTheOldPassword() = runTest {
        flow.push(AccessRoute.SIGN_IN)
        flow.push(AccessRoute.RECOVERY_RUT)
        attempt.recoveryRut.rutChanged(RUT)
        assertEquals(StepOutcome.Advanced, attempt.recoveryRut.submit())
        attempt.recoveryCode.codeChanged(AccessScript.SMS_CODE)
        assertEquals(StepOutcome.Advanced, attempt.recoveryCode.submit())

        attempt.newPassword.passwordChanged(AccessScript.PASSWORD)
        attempt.newPassword.repeatedChanged(AccessScript.PASSWORD)
        attempt.newPassword.submit()
        assertTrue(attempt.newPassword.state.value.reused)
        attempt.newPassword.passwordChanged("Distinta123!")
        attempt.newPassword.repeatedChanged("Distinta123!")
        assertEquals(StepOutcome.Advanced, attempt.newPassword.submit())
        path(AccessRoute.RECOVERY_DONE)
    }

    @Test
    fun aDoctorSignsInThroughTheSameDoorAndOpensTheDoctorsApp() = runTest {
        // No doctor entrance: the answer to sign-in says which app opens (ADR-0056).
        flow.push(AccessRoute.SIGN_IN)
        attempt.signIn.rutChanged(AccessScript.DOCTOR_RUT)
        attempt.signIn.passwordChanged(AccessScript.PASSWORD)
        assertEquals(StepOutcome.Advanced, attempt.signIn.submit())
        assertEquals(StepOutcome.Advanced, attempt.quickSignIn.activate())
        assertEquals(AccessRole.DOCTOR, flow.finished.value)
    }

    @Test
    fun theDesignsPasswordRules() {
        assertTrue(PasswordRules.of("Synthetic1!").allMet)
        assertFalse(PasswordRules.of("synthetic1!").allMet)
        assertFalse(PasswordRules.of("Short1!").allMet)
        assertFalse(PasswordRules.of("NoDigitsHere!").allMet)
        assertFalse(PasswordRules.of("Synthetic1").allMet, "no special character")
        assertFalse(PasswordRules.of("Synthetic 1").allMet, "a space is not a special character")
        assertTrue(PasswordRules.of("Synthetic1ñ#").hasSymbol)
    }

    @Test
    fun aLockOutcomeReadsOnTheButton() {
        assertEquals(StepOutcome.Advanced, stepOutcomeFor(LockOutcome.Unlocked))
        assertEquals(
            StepOutcome.Rejected(AccessText.Counted(Res.plurals.unlock_attempts_left, 2)),
            stepOutcomeFor(LockOutcome.StillLocked(2)),
        )
        assertEquals(
            StepOutcome.Retry(AccessText.Plain(Res.string.unlock_try_again)),
            stepOutcomeFor(LockOutcome.StillLocked(null)),
        )
        assertEquals(StepOutcome.Blocked, stepOutcomeFor(LockOutcome.SessionEnded(SessionEndReason.TOO_MANY_ATTEMPTS)))
    }

    @Test
    fun nothingTypedIsPrinted() {
        attempt.signIn.rutChanged(RUT)
        attempt.signIn.passwordChanged("Synthetic1!")
        attempt.signUpProfile.emailChanged("camila.soto@correo.cl")
        val printed = listOf(attempt.signIn.state.value.toString(), attempt.signUpProfile.state.value.toString())
        assertTrue(printed.none { "456" in it || "Synthetic1!" in it || "camila" in it }, printed.toString())
    }

    @Test
    fun theSecondNameAndSurnameAreOptionalAndTravelTrimmedWhenGiven() {
        val without = SignUpProfileState(
            rut = "18.456.789-K",
            firstName = "Camila",
            firstSurname = "Soto",
            mobile = "+56 9 8765 4321",
            email = "camila.soto@correo.cl",
        )
        assertTrue(without.canSubmit, "a person with one name and one surname can sign up")
        assertNull(without.profile?.secondName)
        assertNull(without.profile?.secondSurname)

        val with = without.copy(secondName = " Andrea ", secondSurname = "Pérez ")
        assertEquals("Andrea", with.profile?.secondName)
        assertEquals("Pérez", with.profile?.secondSurname)
    }

    @Test
    fun theFirstNameAndSurnameAreStillRequired() {
        val judged = SignUpProfileState(rut = "18.456.789-K", judged = true)
        assertTrue(judged.firstNameMissing)
        assertTrue(judged.firstSurnameMissing)
        assertFalse(judged.canSubmit)
    }
}
