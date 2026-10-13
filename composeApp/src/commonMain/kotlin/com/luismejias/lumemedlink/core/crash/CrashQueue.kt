package com.luismejias.lumemedlink.core.crash

import kotlinx.serialization.json.Json

/** At most this many reports wait for the next launch (ADR-0051 §5). */
internal const val MAX_PENDING: Int = 3

/** A report older than this is dropped unsent (ADR-0051 §5). */
internal const val PENDING_LIFETIME_MILLIS: Long = 7L * 24 * 60 * 60 * 1000

private const val PREFIX = "crash-"
private const val SUFFIX = ".json"

private val queueJson = Json { ignoreUnknownKeys = true }

/**
 * Where pending reports wait: small files in the app's own directory, excluded from backup (ADR-0051 §5). Synchronous
 * on purpose — the write happens inside a crash handler, where there is no time for a coroutine — and it swallows its
 * own file errors: a full disk must not become the crash that is remembered.
 */
internal interface CrashFileStore {
    fun write(name: String, text: String)

    fun read(name: String): String?

    fun delete(name: String)

    fun names(): List<String>
}

/** A report that waits, under the file name the queue gave it. */
internal data class PendingCrash(val name: String, val report: CrashReport)

/**
 * The bounded queue of ADR-0051 §5: three reports at most, seven days at most, one file each. It never throws — a
 * crash handler that throws hides the first crash behind its own (§7).
 */
internal class CrashQueue(private val files: CrashFileStore) {

    /**
     * Keeps [report] for the next launch, dropping the oldest beyond [MAX_PENDING]. The store swallows its own file
     * errors, so this does not throw on a full or read-only disk; the capture hands control back either way.
     */
    fun record(report: CrashReport, nowEpochMillis: Long) {
        files.write("$PREFIX$nowEpochMillis$SUFFIX", queueJson.encodeToString(CrashReport.serializer(), report))
        ours().sortedByDescending { it.second }.drop(MAX_PENDING).forEach { files.delete(it.first) }
    }

    /** What waits, oldest first; expired and unreadable files are deleted on the way. */
    @Suppress("SwallowedException")
    fun pending(nowEpochMillis: Long): List<PendingCrash> = ours().sortedBy { it.second }.mapNotNull { (name, at) ->
        if (nowEpochMillis - at > PENDING_LIFETIME_MILLIS) {
            files.delete(name)
            return@mapNotNull null
        }
        val report = try {
            files.read(name)?.let { queueJson.decodeFromString(CrashReport.serializer(), it) }
        } catch (unreadable: IllegalArgumentException) {
            // A file this version cannot read (a decoding error is an IllegalArgumentException): dropped, not retried.
            null
        }
        if (report == null) files.delete(name)
        report?.let { PendingCrash(name, it) }
    }

    /** The server took it: it goes. */
    fun accepted(name: String) {
        files.delete(name)
    }

    private fun ours(): List<Pair<String, Long>> = files.names().mapNotNull { name ->
        name.removePrefix(PREFIX).removeSuffix(SUFFIX).toLongOrNull()
            ?.takeIf { name.startsWith(PREFIX) && name.endsWith(SUFFIX) }
            ?.let { name to it }
    }
}
