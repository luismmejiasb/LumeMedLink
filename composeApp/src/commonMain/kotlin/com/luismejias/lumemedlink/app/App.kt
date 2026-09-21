package com.luismejias.lumemedlink.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.luismejias.lumemedlink.core.logging.DiscardingLogSink
import com.luismejias.lumemedlink.core.logging.LogDetail
import com.luismejias.lumemedlink.core.logging.LogEvent
import com.luismejias.lumemedlink.core.security.NoOpSecurityEventReporter
import com.luismejias.lumemedlink.core.session.InactivityLock
import com.luismejias.lumemedlink.core.session.LockOutcome
import com.luismejias.lumemedlink.core.session.SessionLock
import com.luismejias.lumemedlink.core.session.SessionManager
import com.luismejias.lumemedlink.core.session.TokenStore
import com.luismejias.lumemedlink.core.session.UnlockGate
import com.luismejias.lumemedlink.core.session.UnlockOutcome
import com.luismejias.lumemedlink.core.session.performLogout
import com.luismejias.lumemedlink.core.session.platformInstallSentinel
import com.luismejias.lumemedlink.core.session.rememberSecureStore
import com.luismejias.lumemedlink.core.session.rememberUnlockGate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Inactivity window before re-authentication is demanded (§8.3). */
private const val INACTIVITY_WINDOW_MILLIS = 300_000L

/**
 * Stand-in when the platform cannot host a biometric gate. It reports [UnlockOutcome.Unavailable],
 * which [SessionLock] turns into an ended session — a missing gate is never an open door.
 */
private object AbsentUnlockGate : UnlockGate {
    override suspend fun enroll(): Boolean = false

    override suspend fun unlock(): UnlockOutcome = UnlockOutcome.Unavailable

    override suspend fun clear() = Unit
}

/**
 * Root composable — the single entry point both platform shells render, and the composition root
 * (ADR-0008): it is the ONE place that wires concrete dependencies together.
 *
 * `public` on purpose: this is the Gradle-module boundary consumed by `:androidApp` and the iOS
 * framework. Everything else in this tree starts `internal`.
 *
 * The whole app renders inside [PrivacyScreenScaffold] (ADR-0010), so no screen can exist without
 * the privacy cover.
 */
@Composable
public fun App() {
    val secureStore = rememberSecureStore()
    val unlockGate = rememberUnlockGate(secureStore) ?: AbsentUnlockGate
    val sessionManager = remember(secureStore) {
        SessionManager(TokenStore(secureStore), UnwiredRefreshClient())
    }
    val sessionLock = remember(unlockGate) {
        SessionLock(InactivityLock(INACTIVITY_WINDOW_MILLIS), unlockGate)
    }
    val scope = rememberCoroutineScope()
    // The declared defaults, wired here because this is the composition root (ADR-0008). Both
    // write nothing on purpose — F22 and F23 decided that the default is silence, not that
    // nothing ever calls them.
    val installSentinel = remember { platformInstallSentinel() }
    val logSink = remember { DiscardingLogSink }
    // Still the no-op, and that is now a WIRING gap rather than a missing implementation:
    // `HttpSecurityEventReporter` exists and is tested, but nothing here builds an HttpClient yet
    // (no base URL is configured and no auth flow exists to give it a token). Named so the next
    // reader sees a wire to connect, not a channel to write.
    val securityEvents = remember { NoOpSecurityEventReporter }

    var hasSession by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(sessionLock.isLocked()) }

    // THE WINDOW CLOSES ON ITS OWN. Until 2026-09-21 nothing re-read the lock except the pointer
    // handler below, so five minutes could elapse with the agenda on screen and the app would only
    // notice when somebody touched it — the one moment the person is already looking at it. The
    // threat this app ranks FIRST is the phone left on a table (§8.17), and that is exactly the
    // case with no touches in it.
    //
    // It sleeps for the remaining time rather than polling, and re-asks after waking: if activity
    // slid the window while this was suspended, `millisUntilLock` simply returns a new positive
    // number and it sleeps again. No restart needed, no tick to tune.
    LaunchedEffect(sessionLock, hasSession, locked) {
        if (!hasSession || locked) return@LaunchedEffect
        while (true) {
            val remaining = sessionLock.millisUntilLock()
            if (remaining <= 0L) {
                locked = true
                return@LaunchedEffect
            }
            delay(remaining)
        }
    }

    LaunchedEffect(sessionManager) {
        // The store is a CAPABILITY and a capability can be unavailable at launch. The decision
        // about what to do then lives in probeSession, outside this composable, so a test can
        // assert it (ADR-0025).
        hasSession = probeSession(sessionManager, installSentinel, secureStore, logSink, securityEvents)
    }

    suspend fun endSession() {
        // The contract itself lives in core/session so a test can reach every branch of it; what
        // stays here is the shell's reaction (ADR-0014, amended 2026-09-21). The four steps used to
        // be this inline sequence, which meant the first throw skipped the rest and the shell's
        // dying scope could truncate the erase.
        val outcome = performLogout(sessionManager, secureStore, unlockGate, sessionLock)
        if (!outcome.complete) {
            // Ends the session anyway — fail closed — but the shell now KNOWS the erase was partial.
            // Telling the person is the login slice's job: this screen is a placeholder and a
            // reassuring "sesión cerrada" over a surviving token is exactly the §8.7 vocabulary
            // failure, with the stakes reversed.
            logSink.log(LogEvent.LOGOUT_INCOMPLETE, LogDetail.ofEnumName(outcome.failed.first().name))
        }
        hasSession = false
        locked = true
    }

    PrivacyScreenScaffold {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Observes pointer activity on the INITIAL pass without consuming it, so the
                // inactivity window slides while the doctor is actually using the app. Recording
                // activity can never unlock (InactivityLock enforces that) — it only postpones.
                .pointerInput(sessionLock) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            sessionLock.recordActivity()
                            locked = sessionLock.isLocked()
                        }
                    }
                },
        ) {
            when (resolveDestination(hasSession, locked)) {
                AppDestination.Login -> LoginScreen()

                AppDestination.Locked -> LockedScreen(
                    onUnlockRequested = {
                        scope.launch {
                            when (sessionLock.attemptUnlock()) {
                                LockOutcome.Unlocked -> locked = false
                                is LockOutcome.StillLocked -> locked = true
                                // Too many misses, a changed enrollment, or no biometrics at all:
                                // the session ends and the doctor signs in again. Fail closed.
                                is LockOutcome.SessionEnded -> endSession()
                            }
                        }
                    },
                )

                AppDestination.Home -> HomeScreen(
                    onSignOut = { scope.launch { endSession() } },
                )
            }
        }
    }
}
