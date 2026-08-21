package com.luismejias.lumemedlink.core.session

/**
 * Seam to the backend's refresh operation. The HTTP implementation arrives when the backend
 * answers contract request 0001 (the narrow-scope token) — until then only fakes implement it,
 * and the single-flight logic in [SessionManager] is tested against them.
 *
 * Returns the NEW pair (rotation: the old refresh token is spent), or `null` when the server
 * rejected the refresh definitively — the session dies. Transport failures may throw; the caller
 * treats an exception as "not refreshed, session state unchanged" (a refresh is a POST: the stack
 * never retries it blindly, backend trap T13).
 */
internal interface RefreshClient {
    suspend fun refresh(refreshToken: String): SessionTokens?
}
