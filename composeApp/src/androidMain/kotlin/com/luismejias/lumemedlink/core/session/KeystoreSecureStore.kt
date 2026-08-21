package com.luismejias.lumemedlink.core.session

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEY_ALIAS = "lume_session_tier1"
private const val STORE_DIR = "lume_secure"
private const val GCM_TAG_BITS = 128

/**
 * Tier-1 store on Android (ADR-0009 of this repo, wiring the choice ADR-0005 left open): an
 * AES-256-GCM key that LIVES IN AndroidKeyStore (non-exportable by construction) encrypts each
 * value into a private file under filesDir — zero new dependencies, no SharedPreferences, no
 * deprecated Jetpack Security.
 *
 * The ADR-0005 floor, both pieces:
 * 1. `setUnlockedDeviceRequired(true)` on the key (API 28+; on 26/27 the piece does not exist
 *    and piece 2 carries the floor alone — stated, not hidden).
 * 2. [put] REFUSES on a device with no lock screen (`KeyguardManager.isDeviceSecure == false`):
 *    a clinical-adjacent phone without a lock never comes to hold a session. Fail closed.
 *
 * `setUserAuthenticationRequired(false)` is the tier-1 CONTRACT, not an oversight: silent
 * refresh must read without a biometric prompt. The tier-2 unlock key (auth-per-use,
 * BIOMETRIC_STRONG, invalidated-by-enrollment) is a different key with different parameters and
 * arrives with the shell's biometric gate (ADR-0005 pins those parameters as contract).
 *
 * A value that fails GCM authentication loads as `null` (fail closed: corrupt or key-invalidated
 * means no session, never a crash loop). Files live under [STORE_DIR]; backup is already off
 * app-wide (`allowBackup=false`) and S1.2 adds `dataExtractionRules`.
 */
internal class KeystoreSecureStore(context: Context, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) :
    SecureStore {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, STORE_DIR)
    private val mutex = Mutex()

    override suspend fun put(key: String, value: String): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            check(isDeviceSecure()) {
                "Refusing to store a secret on a device with no lock screen (ADR-0005 floor)."
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
            val ciphertext = cipher.doFinal(value.encodeToByteArray())
            dir.mkdirs()
            // [ivSize][iv][ciphertext] — the IV is Keystore-randomized per encryption.
            fileFor(key).writeBytes(byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + ciphertext)
        }
    }

    override suspend fun get(key: String): String? = withContext(ioDispatcher) {
        mutex.withLock {
            val file = fileFor(key)
            if (!file.exists()) return@withLock null
            val blob = file.readBytes()
            if (blob.isEmpty()) return@withLock null
            val ivSize = blob[0].toInt()
            if (ivSize <= 0 || blob.size < 1 + ivSize) return@withLock null
            val iv = blob.copyOfRange(1, 1 + ivSize)
            val ciphertext = blob.copyOfRange(1 + ivSize, blob.size)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, obtainKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.doFinal(ciphertext).decodeToString()
            } catch (_: AEADBadTagException) {
                null
            }
        }
    }

    override suspend fun remove(key: String): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            fileFor(key).delete()
        }
    }

    override suspend fun wipe(): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            dir.deleteRecursively()
            keyStore().deleteEntry(KEY_ALIAS)
        }
    }

    private fun fileFor(key: String): File = File(dir, "$key.bin")

    private fun isDeviceSecure(): Boolean {
        val keyguard = appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return keyguard.isDeviceSecure
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun obtainKey(): SecretKey {
        (keyStore().getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setUnlockedDeviceRequired(true)
                }
            }
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
