package com.luismejias.lumemedlink.features.patient.settings

import com.luismejias.lumemedlink.core.auth.AccessScript
import com.luismejias.lumemedlink.core.care.CareFailure
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.ContactKind
import com.luismejias.lumemedlink.core.care.PatientProfile
import com.luismejias.lumemedlink.core.care.ScriptedPatientCare
import com.luismejias.lumemedlink.core.session.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ContactChangeTest {
    private val dispatcher = StandardTestDispatcher()
    private var now = 1_000_000L
    private val clock = Clock { now }
    private val care = ScriptedPatientCare(latency = {}, clock = clock)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun profile(): PatientProfile = (care.profile() as CareResult.Ok).value

    private suspend fun opened(kind: ContactKind): ContactChangeViewModel =
        ContactChangeViewModel(care).also { it.open(kind, profile()) }

    @Test
    fun theRightCodeChangesThePhoneAndClosesTheSheet() = runTest(dispatcher) {
        val change = opened(ContactKind.PHONE)
        change.typedChanged("9 1234 5678")
        change.send()
        assertEquals(ContactChangeStep.CODE, change.state.value?.step)
        assertEquals("+56 9 1234 5678", change.state.value?.destination?.replace(' ', ' '))
        assertEquals("+56987654321", profile().mobile?.e164, "nothing changes before the code")
        change.codeChanged(AccessScript.SMS_CODE)
        change.confirm()
        assertNull(change.state.value)
        assertEquals(ContactKind.PHONE, change.done.value)
        assertEquals("+56912345678", profile().mobile?.e164)
    }

    @Test
    fun theRightCodeChangesTheEmail() = runTest(dispatcher) {
        val change = opened(ContactKind.EMAIL)
        change.typedChanged("nueva.direccion@correo.cl")
        change.send()
        change.codeChanged(AccessScript.SMS_CODE)
        change.confirm()
        assertEquals("nueva.direccion@correo.cl", profile().email?.value)
        assertEquals(ContactKind.EMAIL, change.done.value)
    }

    @Test
    fun aWrongCodeCountsDownThenSpendsIt() = runTest(dispatcher) {
        val change = opened(ContactKind.PHONE)
        change.typedChanged("912345678")
        change.send()
        change.codeChanged("000000")
        change.confirm()
        assertEquals(2, change.state.value?.wrongAttemptsLeft)
        change.codeChanged("000000")
        change.confirm()
        assertEquals(1, change.state.value?.wrongAttemptsLeft)
        change.codeChanged("000000")
        change.confirm()
        assertTrue(change.state.value?.expired == true, "the last miss spends the code")
        change.codeChanged(AccessScript.SMS_CODE)
        change.confirm()
        assertTrue(change.state.value?.expired == true, "a spent code stays spent")
        assertEquals("+56987654321", profile().mobile?.e164)
    }

    @Test
    fun aLapsedCodeIsRefusedAndAResendRevivesIt() = runTest(dispatcher) {
        val change = opened(ContactKind.PHONE)
        change.typedChanged("912345678")
        change.send()
        now += AccessScript.CODE_LIFETIME_MILLIS
        change.codeChanged(AccessScript.SMS_CODE)
        change.confirm()
        assertTrue(change.state.value?.expired == true)
        change.resend()
        assertFalse(change.state.value?.expired == true)
        change.codeChanged(AccessScript.SMS_CODE)
        change.confirm()
        assertEquals("+56912345678", profile().mobile?.e164)
    }

    @Test
    fun theCurrentContactIsRefusedBeforeAnythingIsSent() = runTest(dispatcher) {
        val change = opened(ContactKind.PHONE)
        change.typedChanged("9 8765 4321")
        assertTrue(change.state.value?.sameAsCurrent == true)
        assertFalse(change.state.value?.canSend == true)
        change.send()
        assertEquals(ContactChangeStep.ENTER, change.state.value?.step)
        val email = opened(ContactKind.EMAIL)
        email.typedChanged("Camila.Soto@correo.cl")
        assertTrue(email.state.value?.sameAsCurrent == true, "an address is the same whatever its case")
    }

    @Test
    fun aMalformedContactIsSaidAndNotSent() = runTest(dispatcher) {
        val change = opened(ContactKind.EMAIL)
        change.typedChanged("sin-arroba")
        assertTrue(change.state.value?.invalid == true)
        assertFalse(change.state.value?.canSend == true)
    }

    @Test
    fun offlineIsSaidAndTheStepStays() = runTest(dispatcher) {
        val offline = ScriptedPatientCare(failure = CareFailure.Offline, latency = {}, clock = clock)
        val change = ContactChangeViewModel(offline).also { it.open(ContactKind.PHONE, profile()) }
        change.typedChanged("912345678")
        change.send()
        assertEquals(ContactChangeTrouble.OFFLINE, change.state.value?.trouble)
        assertEquals(ContactChangeStep.ENTER, change.state.value?.step)
    }

    @Test
    fun nothingPersonalPrints() = runTest(dispatcher) {
        val change = opened(ContactKind.PHONE)
        change.typedChanged("912345678")
        change.send()
        val printed = change.state.value.toString()
        assertFalse("1234" in printed)
        assertFalse("87654321" in printed)
    }
}
