package com.luismejias.lumemedlink.core.auth

import com.luismejias.lumemedlink.core.session.Clock
import com.luismejias.lumemedlink.shared.ChileanMobile
import com.luismejias.lumemedlink.shared.ChileanRut
import com.luismejias.lumemedlink.shared.EmailAddress
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A clock a test moves by hand. */
internal class ManualClock(var now: Long = 1_000_000L) : Clock {
    override fun nowEpochMillis(): Long = now
}

private fun rut(typed: String) = requireNotNull(ChileanRut.parse(typed))

private fun profile(rutTyped: String = "18.456.789-K") = SignUpProfile(
    rut = rut(rutTyped),
    firstName = "Camila",
    secondName = null,
    firstSurname = "Soto",
    secondSurname = null,
    mobile = requireNotNull(ChileanMobile.parse("+56 9 8765 4321")),
    email = requireNotNull(EmailAddress.parse("camila.soto@correo.cl")),
)

private val CREDENTIALS = SignUpCredentials("Synthetic1", acceptedTerms = true, acceptedDataUse = true)

/** Every row of task 0029's script table — the script IS what the device shows, so it is pinned. */
class ScriptedAccessGatewayTest {
    private val clock = ManualClock()
    private val gateway = ScriptedAccessGateway(clock, latency = {})

    @Test
    fun theScriptedPasswordSignsIn() = runTest {
        assertEquals(AccessResult.Ok(Unit), gateway.signIn(rut("18.456.789-K"), AccessScript.PASSWORD))
    }

    @Test
    fun wrongPasswordsCountDownThenLockForFifteenMinutes() = runTest {
        val who = rut("18.456.789-K")
        val left = (1..4).map { (gateway.signIn(who, "nope") as AccessResult.Failed).failure }
        assertEquals((4 downTo 1).map { AccessFailure.InvalidCredentials(it) }, left)
        val locked = (gateway.signIn(who, "nope") as AccessResult.Failed).failure
        assertEquals(AccessFailure.LockedOut(clock.now + AccessScript.LOCKOUT_MILLIS), locked)
        // Even the right password waits out the lockout.
        assertIs<AccessFailure.LockedOut>((gateway.signIn(who, AccessScript.PASSWORD) as AccessResult.Failed).failure)
        clock.now += AccessScript.LOCKOUT_MILLIS
        assertEquals(AccessResult.Ok(Unit), gateway.signIn(who, AccessScript.PASSWORD))
    }

    @Test
    fun theOfflineRutReachesNothing() = runTest {
        assertEquals(AccessResult.Failed(AccessFailure.Offline), gateway.signIn(rut(AccessScript.OFFLINE_RUT), "x"))
        val offline = gateway.checkProfile(profile(AccessScript.OFFLINE_RUT))
        assertEquals(AccessResult.Failed(AccessFailure.Offline), offline)
    }

    @Test
    fun quickSignInIsRefusedOnlyForTheNoBiometricsRut() = runTest {
        gateway.signIn(rut(AccessScript.NO_BIOMETRICS_RUT), AccessScript.PASSWORD)
        assertEquals(AccessResult.Failed(AccessFailure.BiometricsNotConfigured), gateway.enableQuickSignIn())
        gateway.signIn(rut("18.456.789-K"), AccessScript.PASSWORD)
        assertEquals(AccessResult.Ok(Unit), gateway.enableQuickSignIn())
    }

    @Test
    fun theTakenRutCannotSignUp() = runTest {
        assertEquals(AccessResult.Failed(AccessFailure.RutTaken), gateway.checkProfile(profile(AccessScript.TAKEN_RUT)))
        assertEquals(AccessResult.Ok(Unit), gateway.checkProfile(profile()))
    }

    @Test
    fun aSignUpCodeHasThreeTriesAndTenMinutes() = runTest {
        val sent = (gateway.submitCredentials(profile(), CREDENTIALS) as AccessResult.Ok).value
        assertEquals(clock.now + AccessScript.CODE_LIFETIME_MILLIS, sent.expiresAtEpochMillis)
        assertEquals(clock.now + AccessScript.RESEND_INTERVAL_MILLIS, sent.resendAvailableAtEpochMillis)
        assertEquals(AccessResult.Failed(AccessFailure.WrongCode(2)), gateway.confirmSignUp("000000"))
        assertEquals(AccessResult.Failed(AccessFailure.WrongCode(1)), gateway.confirmSignUp("000000"))
        assertEquals(AccessResult.Failed(AccessFailure.CodeExpired), gateway.confirmSignUp("000000"))
        // A spent code stays spent, even the right one.
        assertEquals(AccessResult.Failed(AccessFailure.CodeExpired), gateway.confirmSignUp(AccessScript.SMS_CODE))

        gateway.resendSignUpCode()
        clock.now += AccessScript.CODE_LIFETIME_MILLIS
        assertEquals(AccessResult.Failed(AccessFailure.CodeExpired), gateway.confirmSignUp(AccessScript.SMS_CODE))

        gateway.resendSignUpCode()
        val created = (gateway.confirmSignUp(AccessScript.SMS_CODE) as AccessResult.Ok).value
        assertEquals("Camila", created.givenNames)
    }

    @Test
    fun recoveryIsNeutralAndRefusesTheOldPassword() = runTest {
        assertIs<AccessResult.Ok<CodeDispatch>>(gateway.requestPasswordReset(rut("9.876.543-3")))
        assertEquals(AccessResult.Ok(Unit), gateway.verifyResetCode(AccessScript.SMS_CODE))
        assertEquals(AccessResult.Failed(AccessFailure.PasswordReused), gateway.setNewPassword(AccessScript.PASSWORD))
        assertEquals(AccessResult.Ok(Unit), gateway.setNewPassword("Distinta123"))
        // The verification is spent with the password it allowed.
        assertEquals(AccessResult.Failed(AccessFailure.Unavailable), gateway.setNewPassword("Otra4567"))
    }

    @Test
    fun linkingCodesLeadToTheirScreens() = runTest {
        assertIs<AccessResult.Ok<LinkedDoctor>>(gateway.lookUp(AccessScript.LINK_CODE))
        assertEquals(AccessResult.Ok(Unit), gateway.confirmLink())
        assertIs<AccessResult.Ok<LinkedDoctor>>(gateway.lookUp(AccessScript.UNVERIFIED_LINK_CODE))
        assertEquals(AccessResult.Failed(AccessFailure.DoctorUnverified), gateway.confirmLink())
        assertIs<AccessResult.Ok<LinkedDoctor>>(gateway.lookUp(AccessScript.NOT_LINKED_CODE))
        assertEquals(AccessResult.Failed(AccessFailure.DoctorNotLinked), gateway.confirmLink())
        assertEquals(AccessResult.Failed(AccessFailure.LinkCodeSpent), gateway.lookUp("ZZZZ9999"))
    }

    @Test
    fun theScriptHoldsNoRealLookingPeopleInItsPrintouts() {
        val printed = listOf(profile().toString(), CREDENTIALS.toString())
        assertTrue(printed.none { "Camila" in it || "Synthetic1" in it || "4321" in it }, printed.toString())
    }
}
