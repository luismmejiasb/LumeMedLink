package com.luismejias.lumemedlink.core.session

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.Signature
import java.security.SignatureException
import java.security.UnrecoverableKeyException
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.coroutines.resume

private const val UNLOCK_KEY_ALIAS = "lume_session_tier2_unlock"
private const val UNLOCK_CHALLENGE_KEY = "unlock_challenge_v1"
private const val CHALLENGE_BYTES = 32
private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

/**
 * Android tier-2 gate (ADR-0005, ADR-0011): an **EC key pair living in AndroidKeyStore** whose
 * private half the OS refuses to use without a fresh strong-biometric match.
 *
 * How "anchored to key material, never a boolean" is realized here: unlocking means producing a
 * signature over a stored random challenge and verifying it against the key's public half. Only
 * the hardware-held private key can produce that signature, and the OS will not let it be used
 * until BiometricPrompt reports a match — so the answer to "is this person allowed back in?" is a
 * cryptographic fact, not a flag some patched code could set to true.
 *
 * The three key parameters are CONTRACT, not implementation taste (ADR-0005 says changing them
 * changes the security claim and needs the ADR reopened) — and `Scripts/check-biometric-contract.sh`
 * fails the build if they drift:
 * 1. `setUserAuthenticationRequired(true)` — the key is useless without a user authentication.
 * 2. Authentication **per use** — `setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)` on
 *    API 30+, and its predecessor `setUserAuthenticationValidityDurationSeconds(-1)` (-1 means
 *    per-use; any positive value would be a time window and would silently void point 3) below it.
 * 3. `setInvalidatedByBiometricEnrollment(true)` — enrolling a new fingerprint destroys the key,
 *    so a face added by whoever holds the phone cannot inherit the doctor's session.
 *
 * Strong biometrics only: no `DEVICE_CREDENTIAL` fallback anywhere, because a PIN fallback would
 * both weaken the gate and void the invalidation property that point 3 buys.
 */
internal class BiometricUnlockGate(private val activity: FragmentActivity, private val secureStore: SecureStore) :
    UnlockGate {

    override suspend fun enroll(): Boolean = withContext(Dispatchers.Default) {
        if (!biometricsUsable()) return@withContext false
        deleteKey()
        try {
            generateKeyPair()
        } catch (_: java.security.GeneralSecurityException) {
            return@withContext false
        }
        val challenge = ByteArray(CHALLENGE_BYTES).also { java.security.SecureRandom().nextBytes(it) }
        secureStore.put(UNLOCK_CHALLENGE_KEY, Base64.getEncoder().encodeToString(challenge))
        true
    }

    override suspend fun unlock(): UnlockOutcome {
        if (!biometricsUsable()) return UnlockOutcome.Unavailable
        val challenge = secureStore.get(UNLOCK_CHALLENGE_KEY)
            ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
            ?: return UnlockOutcome.Unavailable

        val entry = loadKeyEntry() ?: return UnlockOutcome.Unavailable
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        try {
            // The invalidation check happens HERE, before any prompt: initializing a signature
            // with a key destroyed by a new biometric enrollment throws, and that is precisely
            // the event the tier exists to detect.
            signature.initSign(entry.privateKey)
        } catch (_: KeyPermanentlyInvalidatedException) {
            return UnlockOutcome.Invalidated
        } catch (_: java.security.InvalidKeyException) {
            return UnlockOutcome.Unavailable
        }

        return when (val prompted = promptForSignature(signature)) {
            is PromptResult.Succeeded -> verifySignature(prompted.signature, challenge, entry)
            PromptResult.Cancelled -> UnlockOutcome.Cancelled
            PromptResult.Failed -> UnlockOutcome.Failed
            PromptResult.Unavailable -> UnlockOutcome.Unavailable
            PromptResult.Invalidated -> UnlockOutcome.Invalidated
        }
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.Default) {
        deleteKey()
        secureStore.remove(UNLOCK_CHALLENGE_KEY)
    }

    private fun verifySignature(
        signature: Signature,
        challenge: ByteArray,
        entry: KeyStore.PrivateKeyEntry,
    ): UnlockOutcome = try {
        signature.update(challenge)
        val signed = signature.sign()
        val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
        // The public half needs no authentication to read — verification is the proof that the
        // PRIVATE half was released by the OS moments ago.
        verifier.initVerify(entry.certificate.publicKey)
        verifier.update(challenge)
        if (verifier.verify(signed)) UnlockOutcome.Unlocked else UnlockOutcome.Failed
    } catch (_: SignatureException) {
        UnlockOutcome.Failed
    }

    private sealed interface PromptResult {
        data class Succeeded(val signature: Signature) : PromptResult
        data object Cancelled : PromptResult
        data object Failed : PromptResult
        data object Unavailable : PromptResult
        data object Invalidated : PromptResult
    }

    private suspend fun promptForSignature(signature: Signature): PromptResult = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val prompt = BiometricPrompt(
                activity,
                java.util.concurrent.Executors.newSingleThreadExecutor(),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val signed = result.cryptoObject?.signature
                        if (continuation.isActive) {
                            continuation.resume(
                                if (signed == null) PromptResult.Failed else PromptResult.Succeeded(signed),
                            )
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        // errString is the OS's localized text; it is never logged (§8.1).
                        if (continuation.isActive) continuation.resume(mapError(errorCode))
                    }

                    // Deliberately NOT resumed here: a single mismatch leaves the prompt open
                    // so the user can retry. The terminal verdict always arrives as an error.
                    override fun onAuthenticationFailed() = Unit
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Desbloquear LumeMedLink")
                .setSubtitle("Confirma tu identidad para volver a tu sesión")
                .setNegativeButtonText("Cancelar")
                // Strong biometrics only — no device-credential fallback (ADR-0005).
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setConfirmationRequired(false)
                .build()
            prompt.authenticate(info, BiometricPrompt.CryptoObject(signature))
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }

    private fun mapError(errorCode: Int): PromptResult = when (errorCode) {
        // Dismissing is NOT a failed attempt (ADR-0020 of LumeMed, mirrored): counting it would
        // log a doctor out for putting the phone down.
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_CANCELED,
        -> PromptResult.Cancelled

        BiometricPrompt.ERROR_NO_BIOMETRICS,
        BiometricPrompt.ERROR_HW_NOT_PRESENT,
        BiometricPrompt.ERROR_HW_UNAVAILABLE,
        -> PromptResult.Unavailable

        else -> PromptResult.Failed
    }

    private fun biometricsUsable(): Boolean =
        BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun loadKeyEntry(): KeyStore.PrivateKeyEntry? = try {
        keyStore().getEntry(UNLOCK_KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
    } catch (_: UnrecoverableKeyException) {
        null
    } catch (_: KeyStoreException) {
        null
    }

    private fun deleteKey() {
        runCatching { keyStore().deleteEntry(UNLOCK_KEY_ALIAS) }
    }

    private fun generateKeyPair() {
        generateUnlockKeyPair(UNLOCK_KEY_ALIAS)
    }
}

/**
 * Generates the tier-2 key pair under [alias] with the ADR-0005 parameters.
 *
 * Deliberately a top-level `internal` function rather than a private method: the instrumented test
 * `UnlockKeyContractTest` generates a key with it and then asks a REAL Android runtime, through
 * `KeyInfo`, whether the properties actually took. A grep gate can only prove the source says the
 * right words; this proves the operating system agrees.
 */
internal fun generateUnlockKeyPair(alias: String) {
    val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
    val builder = KeyGenParameterSpec.Builder(
        alias,
        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
    )
        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
        .setDigests(KeyProperties.DIGEST_SHA256)
        .setUserAuthenticationRequired(true)
        .setInvalidatedByBiometricEnrollment(true)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Timeout 0 == authenticate for EVERY use, restricted to strong biometrics.
        builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
    } else {
        builder.requirePerUseAuthenticationLegacy()
    }
    generator.initialize(builder.build())
    generator.generateKeyPair()
}

/**
 * Pre-API-30 spelling of "authenticate for every use": -1 is per-use, and it is the ONLY argument
 * this app ever passes — a positive value would be a time window and would silently void
 * `setInvalidatedByBiometricEnrollment` (which is why the gate script rejects one).
 *
 * The suppression is narrow and deliberate: the method is deprecated in favour of
 * `setUserAuthenticationParameters`, which starts at API 30, while this app's minSdk is 26.
 * Removing this branch would leave API 26–29 devices with no per-use requirement at all — a silent
 * security downgrade for the oldest devices, which is worse than a deprecation warning.
 */
@Suppress("DEPRECATION")
private fun KeyGenParameterSpec.Builder.requirePerUseAuthenticationLegacy() {
    setUserAuthenticationValidityDurationSeconds(-1)
}
