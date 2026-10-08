package com.luismejias.lumemedlink.core.input

import cl.lume.uicomposer.components.LumeFieldKeyboard
import cl.lume.uicomposer.components.LumeTextCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SensitiveTextFieldTest {

    @Test
    fun everyFieldIsTypedVerbatim() {
        // Verbatim is the only case the kit types without capitals AND without corrections; the
        // other two keep the platform's corrections, which is how a typed value enters the
        // keyboard's vocabulary (ADR-0013). Every purpose, every format.
        SensitiveFieldPurpose.entries.forEach { purpose ->
            SensitiveFieldFormat.entries.forEach { format ->
                assertEquals(
                    LumeTextCase.Verbatim,
                    kitFieldRequestFor(purpose, format).textCase,
                    "$purpose/$format asks the kit for a keyboard that learns",
                )
            }
        }
    }

    @Test
    fun aCredentialIsAlwaysTheMaskedFieldWhateverTheFormat() {
        // Even when the caller asks for an email keyboard: the "IMEs do not learn from password
        // fields" guarantee is not the caller's to trade away.
        SensitiveFieldFormat.entries.forEach { format ->
            assertTrue(kitFieldRequestFor(SensitiveFieldPurpose.CREDENTIAL, format).masked)
        }
    }

    @Test
    fun personalDataKeepsTheKeyboardItNeeds() {
        // A phone field must show the phone keypad; hardening must not make fields unusable, or
        // the next slice will route around the primitive.
        assertEquals(
            LumeFieldKeyboard.Phone,
            kitFieldRequestFor(SensitiveFieldPurpose.PERSONAL_DATA, SensitiveFieldFormat.PHONE).keyboard,
        )
        assertEquals(
            LumeFieldKeyboard.Email,
            kitFieldRequestFor(SensitiveFieldPurpose.PERSONAL_DATA, SensitiveFieldFormat.EMAIL).keyboard,
        )
    }

    @Test
    fun aRutIsTheKitsDocumentFieldTypedVerbatim() {
        val rut = kitFieldRequestFor(SensitiveFieldPurpose.PERSONAL_DATA, SensitiveFieldFormat.RUT)
        assertTrue(rut.document)
        assertEquals(LumeTextCase.Verbatim, rut.textCase)
        SensitiveFieldFormat.entries.filter { it != SensitiveFieldFormat.RUT }.forEach { format ->
            assertFalse(kitFieldRequestFor(SensitiveFieldPurpose.PERSONAL_DATA, format).document, "$format")
        }
        assertFalse(kitFieldRequestFor(SensitiveFieldPurpose.CREDENTIAL, SensitiveFieldFormat.RUT).document)
    }

    @Test
    fun personalDataIsNeverMasked() {
        SensitiveFieldFormat.entries.forEach { format ->
            assertFalse(
                kitFieldRequestFor(SensitiveFieldPurpose.PERSONAL_DATA, format).masked,
                "a doctor must be able to read back a phone number they are correcting",
            )
        }
    }
}
