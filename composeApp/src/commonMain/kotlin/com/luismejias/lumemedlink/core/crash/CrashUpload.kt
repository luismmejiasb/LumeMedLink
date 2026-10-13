package com.luismejias.lumemedlink.core.crash

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Sends one report; `true` when the server took it. Never retried by the caller within a launch (trap T13). */
internal fun interface CrashReportSender {
    suspend fun send(submission: CrashSubmission): Boolean
}

/**
 * The stand-in until the backend has the endpoint (request `0009`, not sent). It is NOT a discard dressed as a feature:
 * it answers "not taken", so the reports stay queued — bounded at three and seven days — and its name says what it is,
 * the lesson of `NoOpSecurityEventReporter`.
 */
internal object NoEndpointCrashReportSender : CrashReportSender {
    override suspend fun send(submission: CrashSubmission): Boolean = false
}

/**
 * The next launch's one pass over the queue (ADR-0051 §5): each report once, the key attached now and only with consent
 * (§3), and what the server took is deleted. A failure leaves the report for the next launch; it never throws.
 */
internal suspend fun flushCrashReports(
    queue: CrashQueue,
    consent: CrashConsent,
    sender: CrashReportSender,
    nowEpochMillis: Long,
) {
    for (pending in queue.pending(nowEpochMillis)) {
        val accepted = try {
            sender.send(CrashSubmission(pending.report, consent.keyForSending()))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") failure: Exception) {
            currentCoroutineContext().ensureActive()
            false
        }
        if (accepted) queue.accepted(pending.name)
    }
}
