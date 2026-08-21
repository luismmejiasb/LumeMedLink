package com.luismejias.lumemedlink.core.session

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import kotlin.test.assertTrue
import kotlin.test.fail

/** Survives between the two phases on purpose: the Keystore entry is what carries the state. */
private const val PERSISTENT_ALIAS = "lume_test_tier2_invalidation"

/**
 * The end-to-end proof of the property the whole tier is paid for: **enrolling a new biometric
 * destroys the key**, so whoever holds the phone cannot add their own fingerprint and inherit the
 * doctor's session (ADR-0005, ADR-0011).
 *
 * **Do not run these two methods with `connectedAndroidDeviceTest`.** That task reinstalls the test
 * APK, and Android wipes an app's Keystore entries on uninstall (the asymmetry ADR-0005 declares),
 * so the key disappears between phases for a reason that has nothing to do with biometrics — which
 * looks exactly like the property passing. Drive them with `Scripts/verify-tier2-invalidation.sh`,
 * which runs the already-installed instrumentation via `am instrument` (no reinstall) and, before
 * trusting phase B, runs it once as a CONTROL that is required to FAIL.
 *
 * The sequence, all of it necessary:
 *   1. `phaseA…` — create the key and confirm the OS lets a signature be initialized with it.
 *   2. `phaseB…` with NO new enrollment — must FAIL ("the key survived"), which is what proves the
 *      key persists across runs and that step 4 is measuring biometrics and not an artifact.
 *   3. Enroll another fingerprint from the shell.
 *   4. `phaseB…` again — must PASS: the same key now throws `KeyPermanentlyInvalidatedException`.
 *
 * Note what none of this needs from the biometric prompt: **nothing**. Invalidation surfaces at
 * `initSign`, before any UI appears — which is why the most important property of the tier is also
 * the one verifiable without driving a fingerprint through a dialog.
 */
@RunWith(AndroidJUnit4::class)
class UnlockKeyInvalidationTest {

    @Test
    fun phaseA_theFreshKeyIsUsableForSigning() {
        runCatching { keyStore().deleteEntry(PERSISTENT_ALIAS) }
        generateUnlockKeyPair(PERSISTENT_ALIAS)

        val key = keyStore().getKey(PERSISTENT_ALIAS, null) as PrivateKey
        try {
            Signature.getInstance("SHA256withECDSA").initSign(key)
        } catch (e: KeyPermanentlyInvalidatedException) {
            fail("The control failed: a brand-new key must not be invalidated already ($e)")
        }
        assertTrue(keyStore().containsAlias(PERSISTENT_ALIAS))
    }

    @Test
    fun phaseB_theSameKeyIsDestroyedByANewEnrollment() {
        assertTrue(
            keyStore().containsAlias(PERSISTENT_ALIAS),
            "phase A must have run first, and on the same device",
        )
        val key = keyStore().getKey(PERSISTENT_ALIAS, null) as PrivateKey

        try {
            Signature.getInstance("SHA256withECDSA").initSign(key)
            fail(
                "The key survived a new biometric enrollment. That means a person who enrolled " +
                    "their own fingerprint on this phone could unlock the doctor's session.",
            )
        } catch (_: KeyPermanentlyInvalidatedException) {
            // Exactly what ADR-0005 pays for. The gate maps this to UnlockOutcome.Invalidated,
            // which SessionLock turns into an ended session.
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}
