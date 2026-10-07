package com.luismejias.lumemedlink.core.session

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class SentinelTestStore : SecureStore {
    val entries = mutableMapOf<String, String>()

    override suspend fun put(key: String, value: String) {
        entries[key] = value
    }

    override suspend fun get(key: String): String? = entries[key]

    override suspend fun remove(key: String) {
        entries.remove(key)
    }

    override suspend fun wipe() {
        entries.clear()
    }
}

/**
 * The Android half of §8.14's declared asymmetry, on the only target where it is the code under test.
 *
 * This assertion used to live in commonTest, where `platformInstallSentinel()` is the ANDROID sentinel
 * on one target and the iOS one on the other — so the test accepted every branch, and a sentinel that
 * started purging on every Android launch passed it (the store came back empty, which its "a purge
 * must actually purge" branch called success). Here it can answer one thing (task `0010`).
 */
class AndroidInstallSentinelTest {

    @Test
    fun theAndroidSentinelReportsEstablishedOnPurpose() = runTest {
        val store = SentinelTestStore()
        store.put(SecureStoreKey.SESSION_TOKENS.storageKey, "kept")

        val outcome = enforceInstallBoundary(platformInstallSentinel(), store)

        assertEquals(
            InstallBoundary.ALREADY_ESTABLISHED,
            outcome,
            "On Android uninstall removes the data dir and the Keystore entries, so nothing can be inherited " +
                "and a purge on every launch would wipe a valid session (ADR-0028).",
        )
        assertEquals("kept", store.get(SecureStoreKey.SESSION_TOKENS.storageKey), "the session must be untouched")
    }
}
