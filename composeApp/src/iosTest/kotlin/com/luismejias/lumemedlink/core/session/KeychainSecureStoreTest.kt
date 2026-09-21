package com.luismejias.lumemedlink.core.session

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * EXECUTABLE SPEC of the SecItem mechanics — add, read, delete-then-add upsert, service-wide
 * wipe. @Ignore'd as a class, with the reason on record (bitácora 0007): the K/N test runner
 * spawns a hostless process on the simulator and securityd grants it NO keychain — every call,
 * data-protection keychain included, answers -25291 errSecNotAvailable. Nothing here is wrong;
 * there is no keychain to talk to. The day the iOS shell exists these tests run hosted, and the
 * passcode-floor semantics are verified on hardware in S1.2. Skipped shows in the report as
 * skipped — a gate's silence is never a verdict, so the silence is labeled.
 *
 * **2026-09-21 — the condition this comment names HAS BEEN MET, and nothing re-ran.** "The day the
 * iOS shell exists these tests run hosted": the shell has existed since 2026-08-25 (ADR-0025).
 * Whether a hosted test runner actually reaches the keychain here is UNVERIFIED — it needs the
 * Kotlin/Native test binary to run inside an app host, which this project does not configure today,
 * so it is a slice and not an edit. Written here rather than left implied, because a condition that
 * quietly comes true is how six tests stay skipped forever while a count says 130.
 *
 * And the count: these six are INCLUDED in the reported iOS total. 130 run, 6 of them do not.
 */
@Ignore
class KeychainSecureStoreTest {

    private val store = KeychainSecureStore()

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
    fun emptyValueSurvivesTheRoundTrip() = runTest {
        store.put("empty", "")
        assertEquals("", store.get("empty"))
    }
}
