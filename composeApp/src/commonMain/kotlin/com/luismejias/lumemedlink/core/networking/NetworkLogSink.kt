package com.luismejias.lumemedlink.core.networking

/**
 * The route words this app is allowed to log, BY NAME.
 *
 * The first version of this file used a shape rule — lowercase letters and hyphens — which reads
 * like a description of route words and is also a description of a tenant slug. `clinica-alemana`
 * matched it exactly, so the clinic's identifier went into every log line. A shape cannot tell a
 * noun from a name; only a list can.
 *
 * Adding a route means adding its words here, deliberately. That cost is the point: the default
 * for an unknown segment is redaction.
 */
private val SAFE_SEGMENTS = setOf(
    "me",
    "orgs",
    "patients",
    "appointments",
    "agenda",
    "contacts",
    "profile",
    "security-events",
    "app-availability",
)

/** A version segment (`v1`, `v2`) — the one digit-bearing segment that is not an identifier. */
private val VERSION_SEGMENT = Regex("^v\\d+$")

/** Stand-in written in place of anything that could identify a person or a record. */
private const val REDACTED_SEGMENT = "{id}"

/**
 * Replaces every path segment that is not a plain route word with [REDACTED_SEGMENT].
 *
 * This exists because the first version of this file redacted the QUERY and kept the whole PATH,
 * which reads as careful and is not: this app's own routes put the identifier IN the path —
 * `/v1/orgs/{tenantId}/patients/{patientId}/appointments` is the shape the backend already
 * publishes. Logging that raw would write a patient identifier to every log line, which is exactly
 * the §8.1 leak the redacting sink was built to prevent.
 *
 * It is an ALLOWLIST, not a denylist, and that direction is deliberate: an unrecognised segment is
 * redacted rather than kept. A future route word that happens to contain a digit will show up as
 * `{id}` — mildly annoying and safe — instead of a new identifier shape slipping through because
 * nobody added it to a list of things to hide.
 */
internal fun redactPath(rawPath: String): String = rawPath.split("/").joinToString("/") { segment ->
    when {
        segment.isEmpty() -> segment
        segment in SAFE_SEGMENTS -> segment
        VERSION_SEGMENT.matches(segment) -> segment
        else -> REDACTED_SEGMENT
    }
}

/**
 * One network log line, redacted BY CONSTRUCTION: these four fields are everything the stack is
 * able to log — no headers, no body, no query (§8.1) — and the path is passed through [redactPath]
 * on the way in.
 *
 * The constructor is private and [of] is the only way to build one, so a caller cannot hand this
 * type a raw path even by accident. Format mirror of LumeMed's log line:
 * `GET /v1/orgs/{id}/patients/{id}/appointments status=200 rid=…`.
 */
internal class NetworkLogEntry private constructor(
    val method: String,
    val path: String,
    val status: Int?,
    val rid: String,
) {
    override fun toString(): String = "$method $path status=${status ?: "-"} rid=$rid"

    companion object {
        /** [rawPath] must be the encoded path WITHOUT the query; the stack strips that separately. */
        fun of(method: String, rawPath: String, status: Int?, rid: String): NetworkLogEntry =
            NetworkLogEntry(method, redactPath(rawPath), status, rid)
    }
}

/**
 * Seam for the redacting log facade (its real implementation arrives with the session slice; the
 * stack must not know where log lines go, only what shape they are allowed to have).
 */
internal interface NetworkLogSink {
    fun log(entry: NetworkLogEntry)
}
