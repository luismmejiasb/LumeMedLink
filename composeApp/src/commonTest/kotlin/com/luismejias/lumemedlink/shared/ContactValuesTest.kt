package com.luismejias.lumemedlink.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// Synthetic only (§9).
class ContactValuesTest {
    @Test
    fun aMobileIsAcceptedHoweverItIsTyped() {
        listOf("+56 9 8765 4321", "56987654321", "9 8765 4321", "987654321").forEach {
            assertEquals("+56987654321", ChileanMobile.parse(it)?.e164, it)
        }
    }

    @Test
    fun anythingThatIsNotAChileanMobileIsRefused() {
        listOf("+56 9 8765 43", "+56 2 2765 4321", "12345", "").forEach { assertNull(ChileanMobile.parse(it), it) }
    }

    @Test
    fun aMobileShowsOnlyItsLastFourDigits() {
        assertEquals("+56 9 •••• 4321", ChileanMobile.parse("987654321")?.masked)
        assertFalse("8765" in ChileanMobile.parse("987654321").toString())
    }

    @Test
    fun anEmailNeedsANameAndADomain() {
        assertNotNull(EmailAddress.parse("camila.soto@correo.cl"))
        assertNull(EmailAddress.parse("camila.soto@correo"))
        assertNull(EmailAddress.parse("camila soto@correo.cl"))
        assertFalse("camila" in EmailAddress.parse("camila.soto@correo.cl").toString())
    }

    @Test
    fun aRutIsReadWithDots() {
        assertEquals("18.456.789-K", ChileanRut.parse("18456789k")?.display)
        assertEquals("9.876.543-3", ChileanRut.parse("9876543-3")?.display)
    }
}
