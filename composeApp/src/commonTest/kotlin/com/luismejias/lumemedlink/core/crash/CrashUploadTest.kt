package com.luismejias.lumemedlink.core.crash

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrashUploadTest {
    private val files = MemoryCrashFileStore()
    private val queue = CrashQueue(files)
    private val consent = CrashConsent(MemorySecureStore()) { "key" }

    private fun record() = queue.record(crashReportOf(IllegalStateException(), emptyList(), TEST_IDENTITY, 1L), 1L)

    @Test
    fun withoutAnEndpointTheReportsStayQueuedBounded() = runTest {
        record()
        flushCrashReports(queue, consent, NoEndpointCrashReportSender, nowEpochMillis = 2L)
        assertEquals(1, queue.pending(nowEpochMillis = 3L).size, "not taken, so kept for the next launch")
    }

    @Test
    fun eachReportIsTriedOnceAndATakenOneIsDeleted() = runTest {
        record()
        var calls = 0
        flushCrashReports(queue, consent, {
            calls++
            true
        }, nowEpochMillis = 2L)
        assertEquals(1, calls, "one attempt per launch: the route has no idempotency key (T13)")
        assertTrue(files.files.isEmpty())
    }

    @Test
    fun aSenderThatThrowsLeavesTheReportAndDoesNotThrow() = runTest {
        record()
        flushCrashReports(queue, consent, { error("network") }, nowEpochMillis = 2L)
        assertEquals(1, queue.pending(nowEpochMillis = 3L).size)
    }

    @Test
    fun withConsentTheKeyTravels() = runTest {
        record()
        consent.setGranted(true)
        val sent = mutableListOf<CrashSubmission>()
        flushCrashReports(queue, consent, {
            sent += it
            true
        }, nowEpochMillis = 2L)
        assertEquals("key", sent.single().crashKey)
    }
}
