package com.luismejias.lumemedlink.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Placeholder screens: unstyled BasicText on purpose — the design system (S0.3) is deferred pending
// the LumeUIComposer verdict, and a styled placeholder would be the first hardcoded-style
// violation. These carry structure, not appearance; they gain the kit's tokens when S0.3 lands.

@Composable
internal fun LoginScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(text = "LumeMedLink")
        // Honest: no sign-in yet. The auth flow (Identity Platform via the backend, ADR-0003) and
        // the HTTP client are near-term slices now that the backend answered request 0001.
        BasicText(text = "Inicio de sesión — pendiente del flujo de auth")
    }
}

@Composable
internal fun LockedScreen(onUnlockRequested: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(text = "Sesión bloqueada")
        // The biometric re-auth behind this is F4; the button is the seam it plugs into.
        BasicText(text = "Desbloquear", modifier = Modifier.padding(top = 16.dp).clickable { onUnlockRequested() })
    }
}

@Composable
internal fun HomeScreen(onSignOut: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(text = "Inicio")
        BasicText(text = "Cerrar sesión", modifier = Modifier.padding(top = 16.dp).clickable { onSignOut() })
    }
}
