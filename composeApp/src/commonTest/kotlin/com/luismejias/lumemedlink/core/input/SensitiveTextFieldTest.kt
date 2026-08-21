package com.luismejias.lumemedlink.core.input

import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SensitiveTextFieldTest {

    @Test
    fun noPurposeEverAllowsAutocorrect() {
        SensitiveFieldPurpose.entries.forEach { purpose ->
            assertEquals(
                false,
                keyboardOptionsFor(purpose).autoCorrectEnabled,
                "autocorrect is how a typed value enters the keyboard's vocabulary: $purpose",
            )
        }
    }

    @Test
    fun noPurposeEverAllowsCapitalizationSuggestions() {
        SensitiveFieldPurpose.entries.forEach { purpose ->
            assertEquals(KeyboardCapitalization.None, keyboardOptionsFor(purpose).capitalization)
        }
    }

    @Test
    fun aCredentialIsAlwaysTypedOnThePasswordKeyboard() {
        // Even when the caller asks for something else: the "IMEs do not learn from password
        // fields" guarantee is not the caller's to trade away.
        assertEquals(
            KeyboardType.Password,
            keyboardOptionsFor(SensitiveFieldPurpose.CREDENTIAL, KeyboardType.Email).keyboardType,
        )
        assertEquals(
            KeyboardType.Password,
            keyboardOptionsFor(SensitiveFieldPurpose.CREDENTIAL, KeyboardType.Text).keyboardType,
        )
    }

    @Test
    fun personalDataKeepsTheKeyboardTypeItNeeds() {
        // A phone field must show the phone keypad; hardening must not make fields unusable, or
        // the next slice will route around the primitive.
        assertEquals(
            KeyboardType.Phone,
            keyboardOptionsFor(SensitiveFieldPurpose.PERSONAL_DATA, KeyboardType.Phone).keyboardType,
        )
    }

    @Test
    fun onlyCredentialsAreMasked() {
        assertTrue(isMasked(SensitiveFieldPurpose.CREDENTIAL))
        assertFalse(
            isMasked(SensitiveFieldPurpose.PERSONAL_DATA),
            "a doctor must be able to read back a phone number they are correcting",
        )
    }
}
