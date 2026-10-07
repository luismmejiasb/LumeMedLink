package com.luismejias.lumemedlink.core.session

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val GCM_TAG_BITS = 128
private const val LEGACY_VALUE = "synthetic-value-from-a-key-without-the-parameter"

/**
 * A tier-1 key made WITHOUT `unlockedDeviceRequired` is not reused where the parameter exists (task
 * `0008`, ADR-0009 amended 2026-10-07).
 *
 * What cannot be reproduced on an emulator, said up front: the operating-system UPDATE itself. A key
 * made on API 26/27 and carried into API 28+ is SEEDED here, as its result — a key under the legacy
 * alias, made without the parameter, and a value it encrypted in the store's own file format. The
 * store cannot tell a seeded key from an inherited one, and that is exactly the point: `KeyInfo`
 * cannot report the parameter either, so the alias is the only evidence there is.
 */
@RunWith(AndroidJUnit4::class)
class Tier1KeyRotationOnDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = KeystoreSecureStore(context)
    private val storeDir = File(context.filesDir, "lume_secure")
    private val legacyBlob = File(storeDir, "${SecureStoreKey.SESSION_TOKENS.storageKey}.bin")

    @AfterTest
    fun cleanUp() = runBlocking { store.wipe() }

    @Test
    fun aKeyMadeWithoutTheParameterIsRetiredWithItsCiphertext() = runBlocking {
        store.wipe()
        val legacyKey = seedLegacyKeyAndValue()
        // CONTROL — the seed is real: the legacy key can still read what it wrote. Without this, a
        // missing value afterwards would prove nothing (the bitácora 0014 lesson).
        assertEquals(LEGACY_VALUE, decryptWith(legacyKey, legacyBlob.readBytes()))

        val read = store.get(SecureStoreKey.SESSION_TOKENS.storageKey)

        assertNull(read, "the store kept serving a value encrypted under a key without unlockedDeviceRequired")
        assertFalse(
            keyStore().containsAlias(TIER1_ALIAS_LEGACY),
            "the key made without the parameter survived: the old API's degradation stays permanent",
        )
        assertFalse(legacyBlob.exists(), "the ciphertext of the retired key survived it")

        // And the store works afterwards, under a key made WITH the parameter.
        store.put(SecureStoreKey.SESSION_TOKENS.storageKey, "synthetic-after-rotation")
        assertEquals("synthetic-after-rotation", store.get(SecureStoreKey.SESSION_TOKENS.storageKey))
        assertTrue(keyStore().containsAlias(TIER1_ALIAS_UNLOCKED_DEVICE_REQUIRED))
    }

    @Test
    fun aKeyMadeWithTheParameterIsNotRotated() = runBlocking {
        store.wipe()
        store.put(SecureStoreKey.SESSION_TOKENS.storageKey, "synthetic-kept")
        val created = keyStore().getCreationDate(TIER1_ALIAS_UNLOCKED_DEVICE_REQUIRED)

        assertEquals("synthetic-kept", store.get(SecureStoreKey.SESSION_TOKENS.storageKey))
        assertEquals(
            created,
            keyStore().getCreationDate(TIER1_ALIAS_UNLOCKED_DEVICE_REQUIRED),
            "a current key must be reused, not remade on every launch",
        )
    }

    /** What an API 26/27 install left behind: the legacy alias, no parameter, and one value under it. */
    private fun seedLegacyKeyAndValue(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                TIER1_ALIAS_LEGACY,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        val key = generator.generateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val ciphertext = cipher.doFinal(LEGACY_VALUE.encodeToByteArray())
        storeDir.mkdirs()
        legacyBlob.writeBytes(byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + ciphertext)
        return key
    }

    private fun decryptWith(key: SecretKey, blob: ByteArray): String {
        val ivSize = blob[0].toInt()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, blob.copyOfRange(1, 1 + ivSize)))
        return cipher.doFinal(blob.copyOfRange(1 + ivSize, blob.size)).decodeToString()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}
