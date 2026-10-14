package com.luismejias.lumemedlink.core.security

import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.NetworkLogSink
import com.luismejias.lumemedlink.core.networking.contract.ContractOperations
import com.luismejias.lumemedlink.core.networking.contract.ReportSecurityEventDto
import com.luismejias.lumemedlink.core.networking.lumeHttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException

/**
 * Posts a security event to the platform over the hardened stack (§8.16, ADR-0023).
 *
 * ## Why this exists now, while the backend is still building its side
 *
 * Because the half that can be wrong is this one. Everything that makes this channel safe is
 * client-side and testable today: that it never throws, that a cancellation is not swallowed, that
 * only an opaque kind crosses, and that it goes through [lumeHttpClient] rather than around it —
 * which is what gives it origin pinning, a redacted log and no retry on a POST. The endpoint
 * answering is the easy part; it is not the part that leaks.
 *
 * ## It never throws, and that is doctrine rather than defensiveness
 *
 * Every caller is *in the middle of applying a protection* — locking a session, refusing an
 * origin, wiping a store. A reporter that throws turns a backend outage into a second failure at
 * the worst possible moment, and the second failure lands in code paths that were written assuming
 * the first one was the emergency. The family's fail-direction table says this channel "no lanza";
 * `SecurityEventReporterContractTest` is that sentence made executable.
 *
 * ## What it does NOT do
 *
 * No retry, no queue, no persistence. A queued security event is personal-data-adjacent history
 * sitting on the device (§8.5 keeps persistence minimal), and a retry on a POST is how one event
 * becomes three — the backend has no idempotency key for this route (trap T13). An event that does
 * not arrive is lost, on purpose, and the platform's view of this app is best-effort by design.
 */
internal class HttpSecurityEventReporter(baseUrl: String, engine: HttpClientEngine, logSink: NetworkLogSink) :
    SecurityEventReporter {
    /**
     * The channel builds its OWN client, so no caller can hand it one that reports the stack's events back into it:
     * behind a captive portal each report would be an interstitial that reports itself, without end.
     */
    private val contract =
        ContractClient(lumeHttpClient(baseUrl, engine, logSink, securityEvents = OWN_TRANSPORT_EVENTS))

    override suspend fun report(kind: SecurityEventKind) {
        try {
            // Translated, never our own name: the platform's vocabulary is closed (see `toPlatformKind`).
            val platformKind = kind.toPlatformKind()
            // The body is the contract's own type (ADR-0061): one field, the closed kind. There is no `message`, no
            // `detail` — a channel that accepts free text is the one that eventually carries a name into a server log
            // (ADR-0023), and the contract does not offer one.
            contract.call(ContractOperations.REPORT, ReportSecurityEventDto(platformKind))
            // Cancellation is re-thrown BEFORE the catch-all. Swallowing it would break structured
            // concurrency (§6): the caller's scope is being torn down and this would pretend
            // otherwise. It is also not a security event — reporting every screen the doctor
            // leaves early would be noise, and noise is how a real event gets missed.
        } catch (cancellation: CancellationException) {
            throw cancellation
            // Then, and only then, the catch-all. Deliberately swallowed and deliberately NOT
            // logged: the exception's message is free text (§8.1 allows one closed vocabulary),
            // and logging a failed security-event report from inside the security-event reporter
            // is the shape that produces loops.
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") failure: Throwable) {
            // The catch above is not enough, and the test that proves it is
            // `aCancelledCallerDoesNotCarryOn`. Cancellation does not always ARRIVE wearing its own
            // type: when the caller's scope dies mid-request, Ktor surfaces the engine's failure
            // wrapped, the stack maps it to `AppError.Retryable`, and this catch-all would eat it
            // — leaving the caller running inside a scope that is being destroyed (§6).
            //
            // `ensureActive()` asks the only question that distinguishes the two cases: "is MY job
            // still alive?". It re-throws the caller's own cancellation when the scope is gone and
            // does nothing when the network merely failed — which is when swallowing is correct.
            // Recognising cancellation by exception type has now failed twice in this repo (F12's
            // timeout, and this); asking the job is the version that does not depend on types.
            currentCoroutineContext().ensureActive()
        }
    }
}

/**
 * What the channel's own transport does with the stack's security events: nothing, on purpose (see the class). Private,
 * so no other client can borrow it and quietly undo `lumeHttpClient`'s required emitter. Behind a portal that answers
 * every request, the interstitial event is lost here too — the channel has no queue (above) — so that event reaches the
 * platform only from networks that intercept selectively.
 */
private val OWN_TRANSPORT_EVENTS: SecurityEventEmitter = SecurityEventEmitter { }
