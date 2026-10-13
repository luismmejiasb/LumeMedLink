package com.luismejias.lumemedlink.core.crash

import com.luismejias.lumemedlink.shared.CalendarDate
import kotlinx.serialization.Serializable

/** This app's own code: the only frames a report keeps (ADR-0051 §1). */
internal const val APP_PACKAGE: String = "com.luismejias.lumemedlink"

/** At most this many frames travel — enough to find the line, too few to describe a session. */
internal const val MAX_FRAMES: Int = 8

private const val MILLIS_PER_DAY = 86_400_000L

/** One frame of this app's code: where, never what it held. */
@Serializable
internal data class CrashFrame(val className: String, val functionName: String, val line: Int? = null)

@Serializable
internal enum class CrashPlatform { ANDROID, IOS }

/** The build and the system the crash happened on. Nothing about the person or the phone's identity. */
internal data class AppIdentity(
    val version: String,
    val build: String,
    val platform: CrashPlatform,
    val osVersion: String,
)

/**
 * A crash, in a closed shape (ADR-0051 §1). There is **no field for a message**: an exception's message is where a URL
 * with a RUT once hid (ADR-0016), and a type that cannot hold one cannot leak one. No key either — the crash key is
 * attached when the report is sent, never when it is captured (ADR-0051 §3).
 */
@Serializable
internal data class CrashReport(
    val exceptionType: String,
    val frames: List<CrashFrame>,
    val appVersion: String,
    val appBuild: String,
    val platform: CrashPlatform,
    val osVersion: String,
    /** The day, `yyyy-mm-dd`, on the device's clock in UTC. Not the time: the day is enough to place a build. */
    val occurredOn: String,
)

/** What leaves the phone: the report, and the crash key only when the person consented (ADR-0051 §3). */
@Serializable
internal data class CrashSubmission(val report: CrashReport, val crashKey: String?)

/**
 * The report for [throwable], from [rawFrames] (the platform's stack, outermost last). The type only — never its
 * message nor its cause's — and only the frames from this app's packages, at most [MAX_FRAMES].
 */
internal fun crashReportOf(
    throwable: Throwable,
    rawFrames: List<CrashFrame>,
    identity: AppIdentity,
    nowEpochMillis: Long,
): CrashReport = CrashReport(
    exceptionType = throwable::class.qualifiedName ?: throwable::class.simpleName ?: "Unknown",
    frames = rawFrames.filter { it.className.startsWith(APP_PACKAGE) }.take(MAX_FRAMES),
    appVersion = identity.version,
    appBuild = identity.build,
    platform = identity.platform,
    osVersion = identity.osVersion,
    occurredOn = CalendarDate.fromEpochDay(nowEpochMillis.floorDiv(MILLIS_PER_DAY)).iso(),
)

private fun CalendarDate.iso(): String = "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
