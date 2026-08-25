package com.luismejias.lumemedlink.core.security

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

/** The path the family's platform already serves (§8.16). Relative: the stack pins the origin. */
private const val SECURITY_EVENTS_PATH = "v1/security-events"

/**
 * The only thing that crosses the wire, and the reason it is a type.
 *
 * One field, and its type is the closed enum. There is no `message`, no `detail`, no `context` —
 * not because callers are trusted to leave them empty, but because a channel that *accepts* free
 * text is the channel that eventually carries "unlock failed for Dr. Pérez, patient 11111111-1"
 * into a server log (ADR-0023). Adding a field here is a visible decision, which is the point.
 *
 * The field NAME is provisional and deliberately isolated in this one declaration: the platform
 * owns the schema and this app has not been given it (see `docs/backend-requests/0004`). A
 * mismatch costs a 400 and nothing else — the reporter never throws, so a wrong name degrades the
 * channel and never the app. Renaming it is one line, in one place, on the day the schema lands.
 */
@Serializable
private data class SecurityEventBody(@SerialName("kind") val kind: SecurityEventKind)

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
internal class HttpSecurityEventReporter(private val client: HttpClient) : SecurityEventReporter {

    override suspend fun report(kind: SecurityEventKind) {
        try {
            client.post(SECURITY_EVENTS_PATH) {
                contentType(ContentType.Application.Json)
                setBody(SecurityEventBody(kind))
            }
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
