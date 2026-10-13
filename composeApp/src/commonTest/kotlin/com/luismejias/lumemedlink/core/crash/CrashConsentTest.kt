package com.luismejias.lumemedlink.core.crash

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrashConsentTest {
    private val store = MemorySecureStore()
    private var made = 0
    private val consent = CrashConsent(store) { "key-${++made}" }

    @Test
    fun noKeyWithoutConsent() = runTest {
        assertNull(consent.keyForSending())
        assertTrue(store.entries.isEmpty(), "nothing is written before the person says yes")
    }

    @Test
    fun withdrawingDeletesTheKeyAndTheNextConsentMakesANewOne() = runTest {
        consent.setGranted(true)
        val first = assertNotNull(consent.keyForSending())

        consent.setGranted(false)
        assertNull(consent.keyForSending())
        assertTrue(store.entries.isEmpty())

        consent.setGranted(true)
        assertNotEquals(first, consent.keyForSending())
    }

    @Test
    fun theLogoutsWipeTakesBothTheConsentAndTheKey() = runTest {
        consent.setGranted(true)
        store.wipe()
        assertNull(consent.keyForSending())
    }

    @Test
    fun theKeyIsAttachedWhenSentNotWhenCaptured() = runTest {
        val queue = CrashQueue(MemoryCrashFileStore())
        val sent = mutableListOf<CrashSubmission>()
        consent.setGranted(true)
        queue.record(crashReportOf(IllegalStateException(), emptyList(), TEST_IDENTITY, 1L), nowEpochMillis = 1L)

        // The person withdraws (or logs out) before the next launch: the report leaves without a key.
        consent.setGranted(false)
        flushCrashReports(queue, consent, {
            sent += it
            true
        }, nowEpochMillis = 2L)

        assertEquals(1, sent.size)
        assertNull(sent.single().crashKey, "a key captured with the crash would leave with whoever is here now")
    }
}
