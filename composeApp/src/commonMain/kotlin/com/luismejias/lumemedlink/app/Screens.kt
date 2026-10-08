package com.luismejias.lumemedlink.app

import androidx.compose.runtime.Composable
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerAlignment
import cl.lume.uicomposer.components.LumeEmptyState
import cl.lume.uicomposer.foundations.LumeIcon

// The home placeholder, dressed by the design kit (S0.3, ADR-0033). The sign-in and the lock screen are
// the auth feature now (features/auth, copied from LumeMed's flow); home waits for the agenda (S1.3).

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
