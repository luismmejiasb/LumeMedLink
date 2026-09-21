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

    /**
     * THE PRODUCTION PATH, and the two assertions this test was missing for three weeks.
     *
     * Its name promised "nothing on disk" and it asserted neither the files nor the Keystore alias;
     * those two assertions lived in the `wipe()` test below, and `wipe()` was a method no logout
     * ever called. Two green tests, a contract described as implemented in three ADRs, and a
     * logout that unlinked one file and left the AES key alive (ADR-0014, amended 2026-09-21).
     *
     * So this one now exercises `performLogout` — what the shell calls — and asserts the same
     * things the wipe test does. If the two ever diverge again, this is the one that must be
     * believed, because it is the one on the path a person's logout actually takes.
     */
    @Test
    fun theProductionLogoutPathLeavesNoFileAndNoKey() = runBlocking {
        val manager = SessionManager(TokenStore(store), FailingRefreshClient())
        val lock = SessionLock(InactivityLock(windowMillis = 300_000L), AlwaysAvailableGate())
        SecureStoreKey.entries.forEach { store.put(it.storageKey, "synthetic-${it.name}") }
        manager.establish(SessionTokens("acc-synthetic", "ref-synthetic", Long.MAX_VALUE))
        assertTrue(manager.hasSession(), "precondition: a session exists on real storage")
        assertTrue(storeDir.listFiles().orEmpty().isNotEmpty(), "precondition: something was written")

        val outcome = performLogout(manager, store, AlwaysAvailableGate(), lock)

        assertTrue(outcome.complete, "steps that failed on real hardware: ${outcome.failed}")
        assertNull(manager.token(), "memory half of the contract")
        assertFalse(manager.hasSession())
        SecureStoreKey.entries.forEach {
            assertNull(store.get(it.storageKey), "${it.name} survived the production logout")
        }
        // Not merely unreadable — gone. A surviving ciphertext file is a forensic artifact.
        assertTrue(
            storeDir.listFiles().orEmpty().isEmpty(),
            "ciphertext files survived the logout: ${storeDir.listFiles()?.map { it.name }}",
        )
        // ADR-0014 point 3, the one that was never implemented: deleting the key is what turns
        // "erased" into "unrecoverable" for any stray copy of the ciphertext.
        assertFalse(
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .containsAlias("lume_session_tier1"),
            "the tier-1 key survived the logout, so old ciphertext would still be decryptable",
        )
    }

    private class AlwaysAvailableGate : UnlockGate {
        override suspend fun enroll(): Boolean = true

        override suspend fun unlock(): UnlockOutcome = UnlockOutcome.Unlocked

        override suspend fun clear() = Unit
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
