package com.luismejias.lumemedlink.core.crash

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Synthetic only (§9): the RUT is fictitious and valid, the URL a test host.
class CrashReportTest {
    private val sensitive =
        IllegalStateException("GET https://api.test.lume/v1/patients?rut=11.111.111-1 failed for Ana")

    private val frames = listOf(
        CrashFrame("android.os.Handler", "dispatchMessage", 99),
        CrashFrame("com.luismejias.lumemedlink.features.patient.rating.RateViewModel", "send", 120),
        CrashFrame("kotlinx.coroutines.DispatchedTask", "run", 1),
    ) + (1..12).map { CrashFrame("com.luismejias.lumemedlink.core.Deep$it", "call", it) }

    @Test
    fun theMessageNeverReachesTheSerializedReport() {
        val report = crashReportOf(sensitive, frames, TEST_IDENTITY, nowEpochMillis = 0L)
        val wire = Json.encodeToString(CrashSubmission.serializer(), CrashSubmission(report, crashKey = null))

        assertFalse(wire.contains("11.111.111-1"), "a RUT in the message must not travel: $wire")
        assertFalse(wire.contains("api.test.lume"), "a URL in the message must not travel: $wire")
        assertFalse(wire.contains("Ana"), "a name in the message must not travel: $wire")
        assertTrue(wire.contains("IllegalStateException"), "the type does travel")
    }

    @Test
    fun onlyThisAppsFramesAndAtMostEight() {
        val report = crashReportOf(sensitive, frames, TEST_IDENTITY, nowEpochMillis = 0L)

        assertTrue(report.frames.all { it.className.startsWith(APP_PACKAGE) }, "no library frame: ${report.frames}")
        assertEquals(MAX_FRAMES, report.frames.size)
        assertEquals("send", report.frames.first().functionName, "the innermost of ours first")
    }

    @Test
    fun theDayIsTheDayAndNotTheTime() {
        // 2026-10-12T23:59:59Z
        val report = crashReportOf(sensitive, frames, TEST_IDENTITY, nowEpochMillis = 1_791_849_599_000L)
        assertEquals("2026-10-12", report.occurredOn)
    }
}
