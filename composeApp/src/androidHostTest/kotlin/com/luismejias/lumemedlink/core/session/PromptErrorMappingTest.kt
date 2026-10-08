package com.luismejias.lumemedlink.core.session

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * "Cancelling costs nothing", pinned at the layer where it can break (task `0010`).
 *
 * `SessionLockTest` proves the policy honours `Cancelled` — through a double of the gate, so it can
 * never see whether a real dismissal ARRIVES as `Cancelled`. That is decided here, by error code, and
 * it is the place the family's own regression lived (ADR-0020 of LumeMed: dismissals folded into
 * failures, a doctor logged out for putting the phone down).
 */
class PromptErrorMappingTest {

    @Test
    fun dismissingThePromptIsNotAMiss() {
        listOf(
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_CANCELED,
        ).forEach { code ->
            assertEquals(UnlockOutcome.Cancelled, unlockOutcomeForPromptError(code), "error code $code")
        }
    }

    @Test
    fun onlyTheThreeDismissalsAreFree() {
        // Every terminal code androidx.biometric 1.1.0 documents (1 to 15). The dangerous mistake is a
        // real failure mapped to Cancelled — a free attempt — so the set is pinned exactly.
        val free = (1..15).filter { unlockOutcomeForPromptError(it) == UnlockOutcome.Cancelled }.toSet()

        assertEquals(
            setOf(
                BiometricPrompt.ERROR_CANCELED,
                BiometricPrompt.ERROR_USER_CANCELED,
                BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            ),
            free,
        )
    }

    @Test
    fun theOperatingSystemsLockoutIsAMiss() {
        listOf(BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT).forEach { code ->
            assertEquals(UnlockOutcome.Failed, unlockOutcomeForPromptError(code), "error code $code")
        }
    }

    @Test
    fun aPromptNobodyTouchedOrABusySensorCostsNothing() {
        // ADR-0040: stays locked, no attempt, no logout.
        listOf(BiometricPrompt.ERROR_TIMEOUT, BiometricPrompt.ERROR_HW_UNAVAILABLE).forEach { code ->
            assertEquals(UnlockOutcome.NotNow, unlockOutcomeForPromptError(code), "error code $code")
        }
    }

    @Test
    fun theCheckBeforeThePromptPostponesOnlyABusySensor() {
        assertEquals(null, unlockOutcomeForCapability(BiometricManager.BIOMETRIC_SUCCESS))
        assertEquals(UnlockOutcome.NotNow, unlockOutcomeForCapability(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE))
        listOf(
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
        ).forEach { result ->
            assertEquals(UnlockOutcome.Unavailable, unlockOutcomeForCapability(result), "capability $result")
        }
    }

    @Test
    fun noUsableBiometricsIsUnavailable() {
        listOf(
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
        ).forEach { code ->
            assertEquals(UnlockOutcome.Unavailable, unlockOutcomeForPromptError(code), "error code $code")
        }
    }

    @Test
    fun aCodeNobodyClassifiedCountsAgainstTheAttacker() {
        assertEquals(UnlockOutcome.Failed, unlockOutcomeForPromptError(UNCLASSIFIED_CODE))
    }

    private companion object {
        const val UNCLASSIFIED_CODE = 9_999
    }
}
