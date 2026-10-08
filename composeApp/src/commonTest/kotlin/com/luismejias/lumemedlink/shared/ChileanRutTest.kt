package com.luismejias.lumemedlink.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// Synthetic RUTs only (§9): bodies chosen for their check digit, computed here, belonging to nobody known.
class ChileanRutTest {

    @Test
    fun theCheckDigitFollowsModulo11() {
        assertEquals('1', ChileanRut.checkDigitFor("11111111"))
        assertEquals('K', ChileanRut.checkDigitFor("10000013"))
        assertEquals('0', ChileanRut.checkDigitFor("10000004"))
    }

    @Test
    fun whatAPersonTypesParsesToOneCanonicalForm() {
        val dotted = assertNotNull(ChileanRut.parse("11.111.111-1"))
        assertEquals("11111111-1", dotted.canonical)
        assertEquals(dotted, ChileanRut.parse("111111111"))
        assertEquals(dotted, ChileanRut.parse(" 11111111 - 1 "))
        assertEquals("10000013-K", assertNotNull(ChileanRut.parse("10.000.013-k")).canonical)
    }

    @Test
    fun aWrongCheckDigitIsNotARut() {
        assertNull(ChileanRut.parse("11.111.111-2"))
        assertNull(ChileanRut.parse("10.000.013-1"))
    }

    @Test
    fun malformedInputIsNotARut() {
        listOf("", "1-9", "abc", "123456789012-3", "00000000-0", "1111111X-1").forEach {
            assertNull(ChileanRut.parse(it), "\"$it\" parsed")
        }
    }

    @Test
    fun itNeverPrintsTheNumber() {
        val rut = assertNotNull(ChileanRut.parse("11.111.111-1"))
        assertFalse("1111" in rut.toString())
        assertFalse("1111" in "$rut")
    }
}
