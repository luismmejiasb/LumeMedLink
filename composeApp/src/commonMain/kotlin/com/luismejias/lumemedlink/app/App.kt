package com.luismejias.lumemedlink.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.luismejias.lumemedlink.core.session.SessionManager
import com.luismejias.lumemedlink.core.session.TokenStore
import com.luismejias.lumemedlink.core.session.rememberSecureStore
import kotlinx.coroutines.launch

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
    val sessionManager = remember(secureStore) {
        SessionManager(TokenStore(secureStore), UnwiredRefreshClient())
    }
    val scope = rememberCoroutineScope()

    var hasSession by remember { mutableStateOf(false) }
    // Locked stays false until F4 wires the inactivity lock to a biometric unlock path — engaging
    // it now would trap the user in a Locked screen with no way out. The destination exists and is
    // tested; it is simply not entered yet.
    val locked = false

    LaunchedEffect(sessionManager) {
        hasSession = sessionManager.hasSession()
    }

    PrivacyScreenScaffold {
        when (resolveDestination(hasSession, locked)) {
            AppDestination.Login -> LoginScreen()
            AppDestination.Locked -> LockedScreen(onUnlockRequested = {})
            AppDestination.Home -> HomeScreen(
                onSignOut = {
                    scope.launch {
                        sessionManager.logout()
                        hasSession = false
                    }
                },
            )
        }
    }
}
