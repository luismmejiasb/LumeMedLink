package com.luismejias.lumemedlink.core.crash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CrashQueueTest {
    private val files = MemoryCrashFileStore()
    private val queue = CrashQueue(files)
    private fun report(type: String) =
        CrashReport(type, emptyList(), "0.1.0", "1", CrashPlatform.IOS, "26", "2026-10-12")

    @Test
    fun atMostThreeWaitAndTheNewestStay() {
        (1..5).forEach { queue.record(report("E$it"), nowEpochMillis = it * 1_000L) }

        val waiting = queue.pending(nowEpochMillis = 6_000L).map { it.report.exceptionType }
        assertEquals(listOf("E3", "E4", "E5"), waiting)
    }

    @Test
    fun aReportOlderThanSevenDaysIsDroppedUnsent() {
        queue.record(report("Old"), nowEpochMillis = 0L)

        assertTrue(queue.pending(nowEpochMillis = PENDING_LIFETIME_MILLIS + 1).isEmpty())
        assertTrue(files.files.isEmpty(), "the file goes too")
    }

    @Test
    fun anUnreadableFileIsDroppedNotRetried() {
        files.write("crash-5.json", "not json")
        assertTrue(queue.pending(nowEpochMillis = 10L).isEmpty())
        assertTrue(files.files.isEmpty())
    }

    @Test
    fun whatTheServerTookIsGone() {
        queue.record(report("E"), nowEpochMillis = 1L)
        val pending = queue.pending(nowEpochMillis = 2L).single()

        queue.accepted(pending.name)

        assertTrue(queue.pending(nowEpochMillis = 3L).isEmpty())
    }
}
