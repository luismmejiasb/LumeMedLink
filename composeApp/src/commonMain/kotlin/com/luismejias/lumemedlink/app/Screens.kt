package com.luismejias.lumemedlink.app

import androidx.compose.runtime.Composable
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerAlignment
import cl.lume.uicomposer.components.LumeEmptyState
import cl.lume.uicomposer.foundations.LumeIcon

// Placeholder screens, dressed by the design kit (S0.3, task 0013, ADR-0033): every region goes
// through LumeContainer and every glyph, colour and spacing comes from LumeTheme — no literal style
// lives here. They still carry structure, not features: the sign-in flow is S1.1.
//
// The copy is Spanish literals for now, as it was; the app's localization layer arrives with the
// first screen that has more to say than a placeholder. The kit receives resolved strings either way.

@Composable
internal fun LoginScreen() {
    LumeContainer(alignment = LumeContainerAlignment.Center) {
        // Honest: no sign-in yet. The auth flow (Identity Platform via the backend, ADR-0003) and
        // the HTTP client are near-term slices now that the backend answered request 0001.
        LumeEmptyState(
            icon = LumeIcon.Key,
            title = "LumeMedLink",
            message = "Inicio de sesión — pendiente del flujo de auth",
        )
    }
}

@Composable
internal fun LockedScreen(onUnlockRequested: () -> Unit) {
    LumeContainer(alignment = LumeContainerAlignment.Center) {
        // The biometric re-auth behind this is F4; the action is the seam it plugs into.
        LumeEmptyState(
            icon = LumeIcon.Shield,
            title = "Sesión bloqueada",
            action = LumeButtonContent.Title("Desbloquear"),
            onAction = onUnlockRequested,
        )
    }
}

@Composable
internal fun HomeScreen(onSignOut: () -> Unit) {
    LumeContainer(alignment = LumeContainerAlignment.Center) {
        LumeEmptyState(
            icon = LumeIcon.Home,
            title = "Inicio",
            action = LumeButtonContent.Title("Cerrar sesión"),
            onAction = onSignOut,
        )
    }
}
