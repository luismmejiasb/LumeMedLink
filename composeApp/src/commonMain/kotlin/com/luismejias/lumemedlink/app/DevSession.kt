package com.luismejias.lumemedlink.app

import com.luismejias.lumemedlink.core.session.SecureStore
import com.luismejias.lumemedlink.core.session.SecureStoreKey
import com.luismejias.lumemedlink.core.session.SecureStoreUnreadableException
import com.luismejias.lumemedlink.core.session.SessionEntryOutcome
import com.luismejias.lumemedlink.core.session.SessionEntryRefusal
import com.luismejias.lumemedlink.core.session.SessionLock
import com.luismejias.lumemedlink.core.session.SessionManager
import com.luismejias.lumemedlink.core.session.SessionTokens
import com.luismejias.lumemedlink.core.session.UnlockGate
import com.luismejias.lumemedlink.core.session.establishSession
import com.luismejias.lumemedlink.core.session.performLogout
import com.luismejias.lumemedlink.core.telemetry.TelemetryConsent
import com.luismejias.lumemedlink.features.auth.flow.AccessRole
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException

/**
 * What a development session stores where the backend's token pair will go (ADR-0066). A MARK, not a credential: the
 * local platform's requests carry the dev token, which never touches the disk (ADR-0062), and nothing sends this.
 * It never expires because nothing could refresh it: the session ends by logout, the lock, or an install boundary.
 */
private const val DEV_SESSION_MARK = "dev-session"

internal fun devSessionTokens(): SessionTokens = SessionTokens(
    accessToken = DEV_SESSION_MARK,
    refreshToken = DEV_SESSION_MARK,
    accessExpiresAtEpochMillis = Long.MAX_VALUE,
)

/** Which side the session belongs to, so an unlocked relaunch opens it again (ADR-0066). Erased by the logout. */
internal class SessionRoleMark(private val store: SecureStore) {
    suspend fun save(role: AccessRole) = store.put(SecureStoreKey.SESSION_ROLE.storageKey, role.name)

    /** The side, or null when none was written or it cannot be read — then there is no session to reopen. */
    suspend fun read(): AccessRole? = try {
        val name = store.get(SecureStoreKey.SESSION_ROLE.storageKey)
        AccessRole.entries.firstOrNull { it.name == name }
    } catch (@Suppress("SwallowedException") unreadable: SecureStoreUnreadableException) {
        null
    }
}

/**
 * A debug build's session against the local platform (ADR-0066): what the access flow will call with the backend's
 * token pair once task 0028 thaws, called today with [devSessionTokens]. Everything else is the real path — the slate
 * erased, the tier-2 key made (the biometrics are REQUIRED: without them the session is refused, ADR-0037), the lock
 * opened.
 *
 * The one thing carried across the erase is the consent the person gave seconds earlier in the sign-up: the slate is
 * cleared of the PREVIOUS session, and that answer belongs to this one.
 */
internal suspend fun beginDevSession(
    role: AccessRole,
    sessionManager: SessionManager,
    secureStore: SecureStore,
    unlockGate: UnlockGate,
    sessionLock: SessionLock,
    consent: TelemetryConsent,
): SessionEntryOutcome {
    val granted = consent.granted()
    val outcome = establishSession(devSessionTokens(), sessionManager, secureStore, unlockGate, sessionLock)
    if (outcome != SessionEntryOutcome.Established) return outcome
    return try {
        SessionRoleMark(secureStore).save(role)
        consent.setGranted(granted)
        outcome
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") unsaved: Exception) {
        currentCoroutineContext().ensureActive()
        // Half a session is no session (ADR-0037): without its side, an unlocked relaunch would have nothing to open.
        performLogout(sessionManager, secureStore, unlockGate, sessionLock)
        SessionEntryOutcome.Refused(SessionEntryRefusal.SESSION_NOT_SAVED)
    }
}
