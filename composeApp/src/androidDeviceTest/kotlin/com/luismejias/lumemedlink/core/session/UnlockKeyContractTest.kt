package com.luismejias.lumemedlink.core.session

import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val TEST_ALIAS = "lume_test_tier2_contract"

/**
 * Asks a REAL Android runtime whether the tier-2 key's security properties actually took.
 *
 * Why this test exists on top of `Scripts/check-biometric-contract.sh`: the gate proves the source
 * code contains the right calls. It cannot prove the operating system honoured them — an API that
 * is quietly ignored on some SDK level, a builder call overwritten later, or a keystore that
 * downgrades the request would all sail past a grep and leave the lock decorative. `KeyInfo` is
 * the OS's own answer about the key it actually created, and these assertions are that answer.
 *
 * Runs with `./gradlew :composeApp:connectedAndroidTest` (a booted device or emulator required).
 * It needs no biometric enrollment: it inspects the key's REQUIREMENTS, never authenticates.
 */
@RunWith(AndroidJUnit4::class)
class UnlockKeyContractTest {

    @AfterTest
    fun cleanUp() {
        runCatching { keyStore().deleteEntry(TEST_ALIAS) }
    }

    @Test
    fun theTierTwoKeyRequiresUserAuthentication() {
        val info = generateAndInspect()

        assertTrue(
            info.isUserAuthenticationRequired,
            "Without this the key is usable with no biometric at all and the gate is decoration (ADR-0005).",
        )
    }

    @Test
    fun theTierTwoKeyIsInvalidatedByANewBiometricEnrollment() {
        val info = generateAndInspect()

        assertTrue(
            info.isInvalidatedByBiometricEnrollment,
            "Whoever holds the phone could enroll their own finger and inherit the session (ADR-0005).",
        )
    }

    @Test
    fun theTierTwoKeyDemandsAuthenticationForEveryUse() {
        val info = generateAndInspect()

        // A validity window > 0 would mean "authenticated recently is good enough", which is the
        // silent downgrade that also voids invalidation-on-enrollment.
        assertEquals(
            0,
            info.userAuthenticationValidityDurationSeconds,
            "Per-use authentication is contract: any positive window voids the invalidation property.",
        )
    }

    @Test
    fun theTierTwoKeyAcceptsStrongBiometricsOnly() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return // API < 30 has no such accessor.
        val info = generateAndInspect()

        assertEquals(
            KeyProperties.AUTH_BIOMETRIC_STRONG,
            info.userAuthenticationType,
            "A device-credential (PIN) path would weaken the gate and void enrollment invalidation.",
        )
    }

    private fun generateAndInspect(): KeyInfo {
        runCatching { keyStore().deleteEntry(TEST_ALIAS) }
        generateUnlockKeyPair(TEST_ALIAS)
        val key = keyStore().getKey(TEST_ALIAS, null) as PrivateKey
        val factory = KeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
        return factory.getKeySpec(key, KeyInfo::class.java)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}
