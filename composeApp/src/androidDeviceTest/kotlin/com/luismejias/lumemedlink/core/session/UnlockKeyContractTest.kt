package com.luismejias.lumemedlink.core.session

import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
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
 * Runs on a booted device or emulator: `./gradlew :composeApp:connectedAndroidDeviceTest`, or — where a phantom
 * adb device breaks that task — the installed APK through `am instrument` (task `0001`). It never authenticates; it
 * inspects the key's REQUIREMENTS. It does need a biometric ENROLLED: Android refuses to create a per-use key
 * without one, which is how this test's first run failed (bitácora 0010). This line used to say the opposite.
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
        // silent downgrade that also voids invalidation-on-enrollment. "Every use" is SPELLED
        // differently on each side of API 30, because the key is built differently there: the API 30+
        // branch asks for timeout 0, the legacy one for -1, and KeyInfo reports what was asked. This
        // asserted 0 everywhere, so on API 26–29 it could only fail (task 0010). The legacy value is
        // read from the platform documentation and was NOT measured: no image below API 37 exists on
        // the machine that wrote this.
        val perUse = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) 0 else -1
        assertEquals(
            perUse,
            info.userAuthenticationValidityDurationSeconds,
            "Per-use authentication is contract: any positive window voids the invalidation property.",
        )
    }

    @Test
    fun theTierTwoKeyAcceptsStrongBiometricsOnly() {
        // SKIPPED, visibly, below API 30 — where KeyInfo has no accessor for it. This used to `return`,
        // which the report counts as a PASS: a test that checked nothing, reported as one that did.
        assumeTrue("API < 30 has no KeyInfo.userAuthenticationType", Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
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
