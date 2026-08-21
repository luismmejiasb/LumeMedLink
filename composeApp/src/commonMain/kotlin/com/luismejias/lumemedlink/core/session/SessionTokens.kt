package com.luismejias.lumemedlink.core.session

import kotlinx.serialization.Serializable

/**
 * The clinician session's token pair (ADR-0003: access <= 15 min, rotating refresh).
 *
 * `toString` is overridden so no token material can ride into a log line or an assertion
 * message by accident — the redacting rule (§8.1) applied at the type, same as NetworkLogEntry.
 */
@Serializable
internal data class SessionTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAtEpochMillis: Long,
) {
    override fun toString(): String = "SessionTokens(access=<redacted>, refresh=<redacted>, " +
        "accessExpiresAtEpochMillis=$accessExpiresAtEpochMillis)"
}
