package com.luismejias.lumemedlink.core.session

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// All data below is synthetic (§9).

/** Records the ORDER of operations, because the order is the design (ADR-0028). */
private class BoundaryRecordingStore(private val failOnWipe: Boolean = false) : SecureStore {
    val log = mutableListOf<String>()
    val entries = mutableMapOf<String, String>()

    override suspend fun put(key: String, value: String) {
        entries[key] = value
    }

    override suspend fun get(key: String): String? = entries[key]

    override suspend fun remove(key: String) {
        entries.remove(key)
    }

    override suspend fun wipe() {
        log += "wipe"
        if (failOnWipe) error("keychain unavailable")
        entries.clear()
    }
}

private class BoundaryFakeSentinel(
    private var marked: Boolean,
    private val failOnMark: Boolean = false,
    private val sharedLog: MutableList<String>,
) : InstallSentinel {
    override suspend fun hasRunBefore(): Boolean = marked

    override suspend fun markHasRun() {
        sharedLog += "mark"
        if (failOnMark) error("could not write the marker")
        marked = true
    }
}

class InstallBoundaryTest {

    @Test
    fun aFreshContainerPurgesTheInheritedSecrets() = runTest {
        val store = BoundaryRecordingStore()
        store.put(SecureStoreKey.SESSION_TOKENS.storageKey, "inherited-from-the-previous-install")
        val sentinel = BoundaryFakeSentinel(marked = false, sharedLog = store.log)

        val outcome = enforceInstallBoundary(sentinel, store)

        assertEquals(InstallBoundary.PURGED_INHERITED_SECRETS, outcome)
        assertTrue(
            store.entries.isEmpty(),
            "On iOS the Keychain outlives the app being deleted. A reinstall that keeps these is a " +
                "session inherited between two people using the same phone (§8.13, §8.17).",
        )
    }

    @Test
    fun theOrderIsPurgeThenMark() = runTest {
        val store = BoundaryRecordingStore()
        val sentinel = BoundaryFakeSentinel(marked = false, sharedLog = store.log)

        enforceInstallBoundary(sentinel, store)

        assertEquals(
            listOf("wipe", "mark"),
            store.log,
            "Marking first would make an INTERRUPTED purge permanent: the container would look " +
                "established while still holding the previous install's secrets, and no later " +
                "launch would ever look again.",
        )
    }

    @Test
    fun anEstablishedContainerIsNotTouched() = runTest {
        val store = BoundaryRecordingStore()
        store.put(SecureStoreKey.SESSION_TOKENS.storageKey, "this-session-is-mine")
        val sentinel = BoundaryFakeSentinel(marked = true, sharedLog = store.log)

        val outcome = enforceInstallBoundary(sentinel, store)

        assertEquals(InstallBoundary.ALREADY_ESTABLISHED, outcome)
        assertEquals(
            emptyList(),
            store.log,
            "CONTROL: an ordinary launch must not wipe anything. Without this assertion a sentinel " +
                "that always purged would pass every test above — and would log the doctor out on " +
                "every single launch.",
        )
        assertEquals("this-session-is-mine", store.get(SecureStoreKey.SESSION_TOKENS.storageKey))
    }

    @Test
    fun aFailedPurgeLeavesTheContainerUNMARKED() = runTest {
        val store = BoundaryRecordingStore(failOnWipe = true)
        val sentinel = BoundaryFakeSentinel(marked = false, sharedLog = store.log)

        val outcome = enforceInstallBoundary(sentinel, store)

        assertEquals(InstallBoundary.FAILED, outcome)
        assertFalse(
            sentinel.hasRunBefore(),
            "Marking anyway — so the user is not bothered twice — converts a TRANSIENT failure " +
                "into a permanent inheritance. Unmarked means the next launch tries again.",
        )
        assertEquals(listOf("wipe"), store.log, "and it must not have reached the mark")
    }

    @Test
    fun aFailedMarkAlsoReportsFailure() = runTest {
        val store = BoundaryRecordingStore()
        val sentinel = BoundaryFakeSentinel(marked = false, failOnMark = true, sharedLog = store.log)

        val outcome = enforceInstallBoundary(sentinel, store)

        assertEquals(InstallBoundary.FAILED, outcome)
        assertFalse(
            sentinel.hasRunBefore(),
            "The purge did happen, so the secrets are gone — but an unmarked container costs one " +
                "extra idempotent purge next launch, which is the cheap direction.",
        )
    }

    @Test
    fun theAndroidSentinelReportsEstablishedOnPurpose() = runTest {
        val store = BoundaryRecordingStore()
        store.put(SecureStoreKey.SESSION_TOKENS.storageKey, "kept")

        val outcome = enforceInstallBoundary(platformInstallSentinel(), store)

        // Pins the DECLARED asymmetry of §8.14 rather than leaving it to a comment. On Android
        // uninstall removes the data dir and the Keystore entries, so nothing can be inherited and
        // a purge on every launch would be pure damage. On iOS the container is fresh in a test
        // process too, so this asserts the branch each platform actually takes.
        when (outcome) {
            InstallBoundary.ALREADY_ESTABLISHED ->
                assertEquals("kept", store.get(SecureStoreKey.SESSION_TOKENS.storageKey))
            InstallBoundary.PURGED_INHERITED_SECRETS ->
                assertTrue(store.entries.isEmpty(), "a purge must actually purge")
            InstallBoundary.FAILED ->
                assertFalse(
                    store.entries.isEmpty(),
                    "A failed boundary must not have half-purged: the store is untouched or the " +
                        "purge completed. (On the iOS test runner the container is real but " +
                        "hostless, so FAILED here is an environment fact, not a defect.)",
                )
        }
    }
}
