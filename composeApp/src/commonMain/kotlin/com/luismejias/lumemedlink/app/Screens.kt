package com.luismejias.lumemedlink.app

import androidx.compose.runtime.Composable
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerAlignment
import cl.lume.uicomposer.components.LumeEmptyState
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.home_sign_out
import com.luismejias.lumemedlink.resources.home_title
import org.jetbrains.compose.resources.stringResource

// The home placeholder, dressed by the design kit (S0.3, ADR-0033); it waits for the main app.

@Composable
internal fun HomeScreen(onSignOut: () -> Unit) {
    LumeContainer(alignment = LumeContainerAlignment.Center) {
        LumeEmptyState(
            icon = LumeIcon.Home,
            title = stringResource(Res.string.home_title),
            action = LumeButtonContent.Title(stringResource(Res.string.home_sign_out)),
            onAction = onSignOut,
        )
    }
}
