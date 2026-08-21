package com.luismejias.lumemedlink.core.session

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The logout contract against the REAL Android store (F5, ADR-0014) — closing the gap bitácora
 * 0007 declared: until now every test of `core/session` ran against a fake map, so the contract was
 * proven as *logic* and merely asserted as *behaviour*. A fake map cannot tell you whether
 * AndroidKeyStore actually persisted anything, whether the ciphertext file really left the disk, or
 * whether the key was truly deleted.
 *
 * Run with `./gradlew :composeApp:connectedAndroidDeviceTest` (a reinstall between runs is
 * harmless here — unlike the invalidation proof, nothing needs to survive across runs).
 */
@RunWith(AndroidJUnit4::class)
class LogoutWipeOnDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = KeystoreSecureStore(context)
    private val storeDir = File(context.filesDir, "lume_secure")

    @AfterTest
    fun cleanUp() = runBlocking { store.wipe() }

    @Test
    fun aSecretSurvivesARoundTripThroughRealHardwareBackedStorage() = runBlocking {
        store.put(SecureStoreKey.SESSION_TOKENS.storageKey, "synthetic-token-payload")

        assertEquals("synthetic-token-payload", store.get(SecureStoreKey.SESSION_TOKENS.storageKey))
    }

    @Test
    fun whatLandsOnDiskIsNotTheSecret() {
        runBlocking { store.put(SecureStoreKey.SESSION_TOKENS.storageKey, "synthetic-token-payload") }

        val onDisk = storeDir.listFiles().orEmpty().joinToString("") { it.readBytes().decodeToString() }

        assertFalse(
            onDisk.contains("synthetic-token-payload"),
            "the value reached the filesystem in the clear — encryption is not happening",
        )
    }

    @Test
    fun logoutLeavesNothingReadableAndNothingOnDisk() = runBlocking {
        val manager = SessionManager(TokenStore(store), FailingRefreshClient())
        manager.establish(SessionTokens("acc-synthetic", "ref-synthetic", Long.MAX_VALUE))
        assertTrue(manager.hasSession(), "precondition: a session exists on real storage")
        assertTrue(storeDir.listFiles().orEmpty().isNotEmpty(), "precondition: something was written")

        manager.logout()

        assertNull(manager.token(), "memory half of the contract")
        assertNull(store.get(SecureStoreKey.SESSION_TOKENS.storageKey), "disk half of the contract")
        assertFalse(manager.hasSession())
    }

    @Test
    fun wipeRemovesTheFilesAndTheKeystoreKeyItself() = runBlocking {
        SecureStoreKey.entries.forEach { store.put(it.storageKey, "synthetic-${it.name}") }
        assertTrue(storeDir.listFiles().orEmpty().isNotEmpty())

        store.wipe()

        SecureStoreKey.entries.forEach {
            assertNull(store.get(it.storageKey), "${it.name} survived the wipe on real storage")
        }
        // Not just unreadable — gone. A surviving ciphertext file is a forensic artifact, and the
        // deleted Keystore key is what makes any stray copy permanently undecryptable.
        assertTrue(
            storeDir.listFiles().orEmpty().isEmpty(),
            "ciphertext files survived the wipe: ${storeDir.listFiles()?.map { it.name }}",
        )
        assertFalse(
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .containsAlias("lume_session_tier1"),
            "the Keystore key survived the wipe, so old ciphertext would still be decryptable",
        )
    }

    private class FailingRefreshClient : RefreshClient {
        override suspend fun refresh(refreshToken: String): SessionTokens? = null
    }
}
