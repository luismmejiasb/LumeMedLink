package com.luismejias.lumemedlink.core.session

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException

/** Why a session was not begun. Each one leaves nothing of the new session behind. */
internal enum class SessionEntryRefusal {
    /**
     * What the previous session left could not be erased, so this one would start on top of it —
     * the account-B-reads-account-A case ADR-0037 exists to close.
     */
    SLATE_NOT_CLEARED,

    /**
     * The tier-2 material could not be made: no usable biometrics. A session the lock will end at its
     * first close is refused here instead, where the person can be told why (ADR-0037).
     */
    TIER2_UNAVAILABLE,

    /** The tokens or the opened window could not be written; what was written is erased again. */
    SESSION_NOT_SAVED,
}

internal sealed interface SessionEntryOutcome {
    data object Established : SessionEntryOutcome

    data class Refused(val reason: SessionEntryRefusal) : SessionEntryOutcome
}

/**
 * The ONE way a session begins (ADR-0037, tasks 0005 and 0020) — the mirror of [performLogout].
 *
 * **On an empty slate.** Everything this app keeps lives under fixed names in one namespace, so a
 * session written over the remains of another one inherits them: the day a cache exists, account B
 * would read account A's agenda on the same phone. A rejected refresh deliberately erases only the
 * token entry (ADR-0036 point 1), so remains are the NORMAL case, not a corner. Erasing at the
 * beginning — instead of keying everything by account — is what makes every future cache safe without
 * its author having to remember.
 *
 * **With its tier-2 material, made now.** `UnlockGate.enroll()` had no caller until 2026-10-07: the
 * lock was built, measured on devices, and would have asked to sign with a key that never existed, so
 * the first lock would have ended every session. This is the only moment with a fresh credential,
 * which is what anchoring re-entry to key material asks for (ADR-0011); enrolling anywhere else —
 * at launch, from the probe — would make the key without anyone having proved who they are.
 *
 * **Or not at all.** Each refusal leaves the store as the logout contract leaves it.
 *
 * The login slice (S1.1) calls this with the pair the backend issued, and nothing else may call
 * `SessionManager.establish` — `Scripts/check-biometric-contract.sh` refuses it anywhere but here.
 */
internal suspend fun establishSession(
    tokens: SessionTokens,
    sessionManager: SessionManager,
    secureStore: SecureStore,
    unlockGate: UnlockGate,
    sessionLock: SessionLock,
): SessionEntryOutcome {
    if (!attempt { secureStore.wipe() } || !attempt { unlockGate.clear() }) {
        return SessionEntryOutcome.Refused(SessionEntryRefusal.SLATE_NOT_CLEARED)
    }
    // Before the tokens are written: a refusal here leaves nothing to undo.
    if (!(attemptFor { unlockGate.enroll() } ?: false)) {
        return SessionEntryOutcome.Refused(SessionEntryRefusal.TIER2_UNAVAILABLE)
    }
    if (!attempt { sessionManager.establish(tokens) } || !attempt { sessionLock.sessionEstablished() }) {
        // Half a session is no session: the logout contract erases whatever did get written.
        performLogout(sessionManager, secureStore, unlockGate, sessionLock)
        return SessionEntryOutcome.Refused(SessionEntryRefusal.SESSION_NOT_SAVED)
    }
    return SessionEntryOutcome.Established
}

// The exception is dropped on purpose: its message is a platform's free text, and §8.1 allows one
// logging path with a closed vocabulary. Cancellation is re-thrown first, by type and by asking the
// job, because a store that wraps a platform call can convert it on the way out (ADR-0026).

private suspend fun attempt(step: suspend () -> Unit): Boolean = attemptFor {
    step()
    true
} ?: false

@Suppress("TooGenericExceptionCaught", "SwallowedException")
private suspend fun <T> attemptFor(step: suspend () -> T): T? = try {
    step()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    currentCoroutineContext().ensureActive()
    null
}
