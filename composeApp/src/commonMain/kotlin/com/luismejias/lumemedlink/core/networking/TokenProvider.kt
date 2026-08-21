package com.luismejias.lumemedlink.core.networking

/**
 * Seam between the stack and the session (ADR-0004 §auth). Born in this slice; the single-flight
 * refresh that implements it arrives with S1.1 — the stack never grows auth logic of its own
 * (§8.2: never home-grown auth).
 */
internal interface TokenProvider {
    /** Current access token, or null when the session is anonymous. Attached as `Bearer`. */
    suspend fun token(): String?

    /**
     * The server answered 401 to a request carrying this provider's token. The session layer
     * decides what that means (refresh, lock, logout); the stack only reports it.
     */
    fun tokenWasRejected()
}
