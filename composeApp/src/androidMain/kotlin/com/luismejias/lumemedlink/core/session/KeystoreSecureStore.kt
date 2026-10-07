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
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The tier-1 key's alias says how the key was MADE, because nothing else can (ADR-0009, amended
 * 2026-10-07). `setUnlockedDeviceRequired` exists from API 28, and `KeyInfo` has no accessor for it:
 * a key's parameters cannot be read back, only its alias. So the alias below is created ONLY with
 * that parameter, and a key under the legacy one is, by definition, a key without it.
 */
internal const val TIER1_ALIAS_UNLOCKED_DEVICE_REQUIRED = "lume_session_tier1_udr"

/** API 26/27, where the parameter does not exist — and every key made before 2026-10-07, on any API. */
internal const val TIER1_ALIAS_LEGACY = "lume_session_tier1"

/** Every alias a tier-1 key can live under. The wipe deletes them all; the device tests assert them all. */
internal val TIER1_KEY_ALIASES: List<String> = listOf(TIER1_ALIAS_UNLOCKED_DEVICE_REQUIRED, TIER1_ALIAS_LEGACY)

/** The alias a key made on this system lives under. One branch, so the alias and the parameter cannot disagree. */
internal fun tier1AliasInUse(sdk: Int = Build.VERSION.SDK_INT): String =
    if (sdk >= Build.VERSION_CODES.P) TIER1_ALIAS_UNLOCKED_DEVICE_REQUIRED else TIER1_ALIAS_LEGACY

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
 * **A key made without piece 1 is retired where piece 1 exists** (task `0008`): an install from API
 * 26/27 keeps its key across an OS update, and reusing it by alias made the old API's degradation
 * permanent. On API 28+ a key under [TIER1_ALIAS_LEGACY] is deleted together with the ciphertext it
 * encrypted — a local logout, the same direction as a device-credential reset — and a new key is made
 * with the parameter. The ciphertext is not re-encrypted: reading it would give the old key one more
 * use, and the tier-1 tokens can be asked for again.
 *
 * A value that fails GCM authentication THROWS `SecureStoreUnreadableException` (ADR-0035; it loaded as
 * `null` until 2026-10-07). Before that change it read: (fail closed: corrupt or key-invalidated
 * means no session, never a crash loop). Files live under [STORE_DIR]; backup is already off
 * app-wide: `allowBackup=false` plus `dataExtractionRules`, which is what actually closes the
 * device-to-device path (F6/ADR-0015 — allowBackup alone does NOT, at targetSdk >= 31).
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
            // EXISTS but cannot be read → it throws, it does not read as "never written" (ADR-0035,
            // task 0003). Until 2026-10-07 every failure here returned null: a blob whose GCM tag did
            // not close — the signature of a tampered file — looked exactly like a first launch, and
            // SECURE_STORE_UNREADABLE was never reported. probeSession already turns this exception into
            // that signal and a sign-in screen (ADR-0025), which is what iOS's store has done all along.
            // Still fail CLOSED: no value comes back, and no caller gets a crash (every caller catches).
            // The key FIRST: obtaining it may retire a key made without unlockedDeviceRequired, and
            // that retirement deletes what it encrypted (task 0008). A file it deleted was not tampered
            // with — as far as the new key is concerned it was never written.
            val secretKey = try {
                obtainKey()
            } catch (_: GeneralSecurityException) {
                throw SecureStoreUnreadableException()
            }
            if (!file.exists()) return@withLock null
            try {
                val blob = file.readBytes()
                val ivSize = blob.firstOrNull()?.toInt() ?: throw SecureStoreUnreadableException()
                if (ivSize <= 0 || blob.size < 1 + ivSize) throw SecureStoreUnreadableException()
                val iv = blob.copyOfRange(1, 1 + ivSize)
                val ciphertext = blob.copyOfRange(1 + ivSize, blob.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.doFinal(ciphertext).decodeToString()
            } catch (_: GeneralSecurityException) {
                throw SecureStoreUnreadableException()
            } catch (_: IOException) {
                throw SecureStoreUnreadableException()
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
            val keyStore = keyStore()
            TIER1_KEY_ALIASES.forEach { keyStore.deleteEntry(it) }
        }
    }

    private fun fileFor(key: String): File = File(dir, "$key.bin")

    private fun isDeviceSecure(): Boolean {
        val keyguard = appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return keyguard.isDeviceSecure
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun obtainKey(): SecretKey {
        val keyStore = keyStore()
        val alias = tier1AliasInUse()
        if (alias != TIER1_ALIAS_LEGACY && keyStore.containsAlias(TIER1_ALIAS_LEGACY)) {
            // Files FIRST: a run that dies between the two lines leaves the legacy key behind and
            // retires it next time, never ciphertext with no key that could ever have made it.
            dir.deleteRecursively()
            keyStore.deleteEntry(TIER1_ALIAS_LEGACY)
        }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .apply {
                // The very condition tier1AliasInUse() chose the alias with, so the alias and the
                // parameter cannot disagree — and the one Lint reads as the API-28 guard it is.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setUnlockedDeviceRequired(true)
                }
            }
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
