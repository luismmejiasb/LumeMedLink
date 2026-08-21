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
import com.luismejias.lumemedlink.core.session.InactivityLock
import com.luismejias.lumemedlink.core.session.LockOutcome
import com.luismejias.lumemedlink.core.session.SessionLock
import com.luismejias.lumemedlink.core.session.SessionManager
import com.luismejias.lumemedlink.core.session.TokenStore
import com.luismejias.lumemedlink.core.session.UnlockGate
import com.luismejias.lumemedlink.core.session.UnlockOutcome
import com.luismejias.lumemedlink.core.session.rememberSecureStore
import com.luismejias.lumemedlink.core.session.rememberUnlockGate
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

    var hasSession by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(sessionLock.isLocked()) }

    LaunchedEffect(sessionManager) {
        hasSession = sessionManager.hasSession()
    }

    suspend fun endSession() {
        sessionManager.logout()
        unlockGate.clear()
        sessionLock.sessionEnded()
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
