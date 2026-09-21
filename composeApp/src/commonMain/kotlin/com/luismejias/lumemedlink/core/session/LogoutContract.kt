package com.luismejias.lumemedlink.core.session

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * The four things a logout erases, as a value so a test can name the one that failed (ADR-0014).
 *
 * They are the ADR's five points, collapsed where one call covers two: [SECRETS] erases both the
 * ciphertext on disk and the key that could decrypt it, which the ADR lists separately because the
 * second is what turns "erased" into "unrecoverable".
 */
internal enum class LogoutStep {
    /** The in-process token pair, and the entry the session wrote. */
    MEMORY,

    /**
     * This app's WHOLE secret namespace and its tier-1 key.
     *
     * The namespace rather than a list of keys, because ADR-0014 says so in as many words: "a
     * forgotten key cannot survive it — the enum exists for the *test*, not to drive the erase".
     * Until 2026-09-21 the code did drive the erase from the enum, and from one entry of it.
     */
    SECRETS,

    /** The tier-2 biometric key and its challenge (ADR-0011). */
    TIER2_MATERIAL,

    /** Back to born-locked, so the next session cannot inherit an open window. */
    LOCK_STATE,
}

/**
 * What the erase actually accomplished. A logout that half-finished is not a logout, and the
 * caller must be able to tell — a UI that says "sesión cerrada" over a surviving token is the
 * vocabulary failure of §8.7 with the stakes reversed.
 */
internal data class LogoutOutcome(val failed: Set<LogoutStep>) {
    val complete: Boolean get() = failed.isEmpty()
}

/**
 * The logout contract of ADR-0014, in one place a test can reach.
 *
 * **Why this function exists at all.** The contract was written in 2026-08-21 and three ADRs
 * described it as implemented; the code did something smaller. `SessionManager.logout()` unlinked
 * ONE file and never touched the tier-1 key, so a phone sold or handed to support kept the AES key
 * alive in AndroidKeyStore next to a ciphertext blob that `File.delete()` had merely unlinked —
 * which is precisely the case point 3 of the ADR claims to close. The device test that "proved" it
 * asserted on `wipe()`, a method no production path called; the test that took the real path
 * asserted neither the files nor the alias, despite promising both in its name.
 *
 * **Every step is attempted, even after one throws.** The previous shape was an inline sequence in
 * the shell composable, so the first failure skipped everything after it: a store that threw on the
 * namespace wipe left the tier-2 key and the lock window alive while the UI had already returned to
 * Login. A partial wipe that reports success is worse than a failure that reports one.
 *
 * **[NonCancellable] on purpose.** The old sequence ran in the shell's `rememberCoroutineScope()`,
 * which dies with the composition — so backgrounding the app mid-logout could truncate the erase
 * exactly when the device is most likely to be leaving the owner's hands. Once this starts, it
 * finishes. It is bounded work: local storage and Keystore, no network.
 *
 * It does NOT revoke anything server-side. ADR-0014's "what logout does NOT do" is unchanged and
 * still the honest part of the contract.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal suspend fun performLogout(
    sessionManager: SessionManager,
    secureStore: SecureStore,
    unlockGate: UnlockGate,
    sessionLock: SessionLock,
): LogoutOutcome = withContext(NonCancellable) {
    val failed = mutableSetOf<LogoutStep>()

    // The catches are deliberately broad and deliberately unguarded by ensureActive(): inside
    // NonCancellable there is no cancellation to observe, and a guard that cannot fire is worse
    // than none. The file carries its reason in check-cancellation-guard.sh's exemption list, where
    // review can see it, rather than as a comment only this file's reader would find.
    suspend fun step(which: LogoutStep, erase: suspend () -> Unit) {
        try {
            erase()
        } catch (failure: Throwable) {
            failed += which
        }
    }

    // Order matters once: memory first, so a caller racing the wipe cannot read a token that the
    // store no longer backs. Everything after is independent.
    step(LogoutStep.MEMORY) { sessionManager.logout() }
    step(LogoutStep.SECRETS) { secureStore.wipe() }
    step(LogoutStep.TIER2_MATERIAL) { unlockGate.clear() }
    step(LogoutStep.LOCK_STATE) { sessionLock.sessionEnded() }

    LogoutOutcome(failed)
}
