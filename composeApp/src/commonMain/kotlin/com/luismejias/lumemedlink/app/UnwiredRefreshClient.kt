package com.luismejias.lumemedlink.app

import com.luismejias.lumemedlink.core.session.RefreshClient
import com.luismejias.lumemedlink.core.session.SessionTokens

/**
 * Placeholder [RefreshClient] until the HTTP one lands. The backend answered contract request 0001
 * (ADR-0036 del backend: per-app audience), so the real refresh is a near-term slice (F9/F10);
 * until then this returns null — which the [com.luismejias.lumemedlink.core.session.SessionManager]
 * reads as "refresh impossible, the session ends." Fail-closed: a shell with no auth flow yet must
 * never behave as if a session could silently continue.
 */
internal class UnwiredRefreshClient : RefreshClient {
    override suspend fun refresh(refreshToken: String): SessionTokens? = null
}
