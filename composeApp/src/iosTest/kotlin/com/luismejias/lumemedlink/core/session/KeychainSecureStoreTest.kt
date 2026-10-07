package com.luismejias.lumemedlink.core.session

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * EXECUTABLE SPEC of the SecItem mechanics — add, read, delete-then-add upsert, a wipe of exactly this
 * app's service.
 *
 * It RUNS HOSTED since 2026-10-07 (task 0015), after six weeks ignored. The reason it could not run was
 * measured twice and was the environment, not the code: Kotlin's test task spawns the binary
 * `--standalone`, and securityd grants a bare process no keychain (-25291 errSecNotAvailable,
 * bitácora 0007); spawned inside a booted simulator it still had no entitlement (-34018). The build now
 * links simulator entitlements into the test binary, and the test task runs on the simulator
 * LUME_IOS_TEST_DEVICE names. Without that variable this class is excluded and the run prints why
 * (composeApp/build.gradle.kts) — never silently.
 *
 * The test binary's keychain group is NOT the app's (`src/iosTest/keychain-tests.entitlements`), so a
 * run can never touch the real app's items on the same simulator.
 */
class KeychainSecureStoreTest {

    private val store = KeychainSecureStore()
    private val foreignItem = ForeignKeychainItem()

    @AfterTest
    fun cleanUp() = runTest {
        store.wipe()
    }

    @Test
    fun roundTrip() = runTest {
        store.put("test-key", "test-value-1")
        assertEquals("test-value-1", store.get("test-key"))
    }

    @Test
    fun putOverwrites() = runTest {
        store.put("test-key", "first")
        store.put("test-key", "second")
        assertEquals("second", store.get("test-key"))
    }

    @Test
    fun missingKeyIsNullNotAnError() = runTest {
        assertNull(store.get("never-written"))
    }

    @Test
    fun removeDeletesOneEntry() = runTest {
        store.put("a", "1")
        store.put("b", "2")
        store.remove("a")
        assertNull(store.get("a"))
        assertEquals("2", store.get("b"))
    }

    @Test
    fun wipeClearsTheWholeService() = runTest {
        store.put("a", "1")
        store.put("b", "2")
        store.wipe()
        assertNull(store.get("a"))
        assertNull(store.get("b"))
    }

    @Test
    fun wipeLeavesAnotherServiceAlone() = runTest {
        // The wipe is this app's OWN service and nothing else (§8.13; keychain-facts): a sweep of the
        // whole class would erase other software's secrets — on iOS a synchronizable sweep would
        // propagate the deletion to the person's other devices. wipeClearsTheWholeService cannot see
        // that defect, since a sweep clears this service too; this test is the one that can.
        foreignItem.add()
        try {
            store.put("a", "1")
            store.wipe()
            assertNull(store.get("a"))
            assertTrue(foreignItem.exists(), "the wipe reached beyond this app's own service")
        } finally {
            foreignItem.delete()
        }
    }

    @Test
    fun emptyValueSurvivesTheRoundTrip() = runTest {
        store.put("empty", "")
        assertEquals("", store.get("empty"))
    }
}
