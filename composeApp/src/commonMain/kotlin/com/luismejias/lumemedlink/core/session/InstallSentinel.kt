package com.luismejias.lumemedlink.core.session

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Answers one question: **has this app's container run before?** (F7, ADR-0028.)
 *
 * The question exists because on iOS the Keychain **survives the app being deleted** while the
 * container does not. Delete the app, install it again, and the new install finds the previous
 * one's secrets sitting there — a session inherited across users of a shared or resold phone,
 * which is exactly what §8.13's logout contract promises cannot happen, happening without a logout.
 *
 * The marker is a FILE in the container, never a preference: the container's **absence** is the
 * fact being detected, and a file has no plist cache and no domain ambiguity to get wrong.
 *
 * It is not an integrity signal and not anti-fraud. It distinguishes "this container has run"
 * from "this container is new" and nothing else — whoever can write in the container is already
 * inside the app's sandbox.
 */
internal interface InstallSentinel {
    /** `true` when a previous launch already marked this container. */
    suspend fun hasRunBefore(): Boolean

    /** Records that this container has run. Called only after a successful purge. */
    suspend fun markHasRun()
}

/** What [enforceInstallBoundary] did, so the caller can decide and a test can assert. */
internal enum class InstallBoundary {
    /** The container had run before. Nothing was inherited and nothing was touched. */
    ALREADY_ESTABLISHED,

    /** A fresh container: this app's secrets were purged and the container marked. */
    PURGED_INHERITED_SECRETS,

    /**
     * The purge or the mark failed. The container is **deliberately left unmarked** so the next
     * launch tries again, and the caller must treat the session as absent.
     */
    FAILED,
}

/**
 * Makes sure a fresh container never inherits the previous installation's secrets (F7, ADR-0028).
 *
 * ## The order is the design, and it is crash-safe in one direction only
 *
 * **Purge, then mark.** Marking first would make an interrupted purge *permanent*: the container
 * would look established while still holding the previous install's secrets, and no later launch
 * would ever look again. In this order an interruption costs one extra purge, and the purge is
 * idempotent.
 *
 * ## Failure is reported, never assumed away
 *
 * If either step fails the container stays **unmarked** and the result is [InstallBoundary.FAILED].
 * Logged out is the honest state when secrets may have been inherited, and the next launch retries.
 * The tempting alternative — mark anyway so the user is not bothered twice — converts a transient
 * failure into a permanent inheritance.
 *
 * ## Scope: this app's own namespace, and nothing else
 *
 * [SecureStore.wipe] removes only what this app owns. Never a sweep of the platform store: on iOS
 * that would destroy other software's state and, with a synchronizable sweep, propagate deletions
 * to the user's other devices through iCloud Keychain. The family rule is that purging someone
 * else's entries is vandalism, and it is a rule this function is in the perfect position to break.
 */
internal suspend fun enforceInstallBoundary(sentinel: InstallSentinel, secureStore: SecureStore): InstallBoundary =
    try {
        if (sentinel.hasRunBefore()) {
            InstallBoundary.ALREADY_ESTABLISHED
        } else {
            secureStore.wipe()
            sentinel.markHasRun()
            InstallBoundary.PURGED_INHERITED_SECRETS
        }
    } catch (cancellation: kotlin.coroutines.cancellation.CancellationException) {
        throw cancellation
        // Broad on purpose and swallowed on purpose: every platform here throws its own type, and the
        // ONE thing that must be true afterwards is that the container was not marked. The exception
        // object is dropped rather than logged — §8.1 allows a closed vocabulary, and a store's message
        // is free text from a platform API.
    } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") failure: Throwable) {
        currentCoroutineContext().ensureActive()
        InstallBoundary.FAILED
    }
