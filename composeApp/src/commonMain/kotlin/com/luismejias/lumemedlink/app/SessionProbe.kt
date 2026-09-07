package com.luismejias.lumemedlink.app

import com.luismejias.lumemedlink.core.logging.LogEvent
import com.luismejias.lumemedlink.core.logging.LumeLogSink
import com.luismejias.lumemedlink.core.security.SecurityEventKind
import com.luismejias.lumemedlink.core.security.SecurityEventReporter
import com.luismejias.lumemedlink.core.session.InstallBoundary
import com.luismejias.lumemedlink.core.session.InstallSentinel
import com.luismejias.lumemedlink.core.session.SecureStore
import com.luismejias.lumemedlink.core.session.SessionManager
import com.luismejias.lumemedlink.core.session.enforceInstallBoundary
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException

/**
 * Asks whether a session exists, and survives a secure store that cannot answer (ADR-0025).
 *
 * ## Why this is not one line inside the composable
 *
 * It used to be, and the first launch of the iOS host killed the process on it: the Keychain
 * answered an error, `KeychainSecureStore.get` threw — correctly — and the exception escaped a
 * `LaunchedEffect` with nothing to catch it. The observed trigger was an unsigned build, which is
 * not a production condition; the *class* is, and the store's own KDoc already names one that is:
 * with `WhenPasscodeSetThisDeviceOnly`, the data-protection keychain answers `errSecNotAvailable`
 * before the device is first unlocked. An app that dies rather than showing a sign-in screen in
 * that window is a worse app, not a safer one.
 *
 * It lives here, outside the composable, for the same reason `keyboardOptionsFor` does: a security
 * property nobody can assert is a security property nobody is keeping. `SessionProbeTest` asserts
 * all three branches.
 *
 * ## What it does NOT do
 *
 * It does not make the store lenient. Turning an unexpected status into `null` inside the store
 * would report "no session" for a store that is actually broken and hide it forever; the store
 * still throws. This is the composition root deciding what to do about it, which is the layer that
 * gets to decide.
 *
 * And it fails in the SAFE direction: `false` means the Login destination. A store this app cannot
 * read costs a sign-in — never an open session.
 */
internal suspend fun probeSession(
    sessionManager: SessionManager,
    sentinel: InstallSentinel,
    secureStore: SecureStore,
    logSink: LumeLogSink,
    securityEvents: SecurityEventReporter,
): Boolean = try {
    // The install boundary runs BEFORE the session is even looked for, and the order matters: on
    // iOS the Keychain outlives the app being deleted, so asking "is there a session?" first would
    // find the PREVIOUS installation's one and answer yes (F7, ADR-0028). Reading before purging is
    // how the guarded thing gets used.
    when (enforceInstallBoundary(sentinel, secureStore)) {
        // The ordinary launch. This container has run before, so nothing was inherited.
        InstallBoundary.ALREADY_ESTABLISHED -> sessionManager.hasSession()

        // A fresh container next to surviving secrets: they are gone now, and there is by
        // definition no session to find. Asking anyway would only read back what was just wiped.
        InstallBoundary.PURGED_INHERITED_SECRETS -> false

        // The purge or the mark failed and the container was deliberately left unmarked. Logged out
        // is the honest state while secrets may have been inherited, and the next launch retries.
        InstallBoundary.FAILED -> false
    }
} catch (cancellation: CancellationException) {
    // Re-thrown before anything else. Swallowing a cancellation breaks structured concurrency
    // (§6), and `runCatching` here would do exactly that — the trap F12 was caught by.
    throw cancellation
    // The exception object is deliberately dropped rather than logged: its message is free text
    // from a platform API, and §8.1 allows one logging path with a CLOSED vocabulary. "Which enum"
    // is the whole record; "what the OS said" is not ours to write down.
} catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") unreadable: Throwable) {
    // Cancellation does not always ARRIVE wearing its own type — a store that wraps a platform
    // call can convert it on the way out, and the catch above would miss it. Asking the job is the
    // version that does not depend on types. This has now been the shape of two real defects in
    // this repo (F12's timeout, and the stack mapping a cancelled caller to `Retryable`), so it is
    // a gate, not a habit: `check-cancellation-guard.sh`.
    currentCoroutineContext().ensureActive()
    logSink.log(LogEvent.SECURE_STORE_UNREADABLE)
    securityEvents.report(SecurityEventKind.SECURE_STORE_UNREADABLE)
    false
}
