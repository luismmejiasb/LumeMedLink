package com.luismejias.lumemedlink.core.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How much the floating tab bar covers at the bottom of the screen in front: zero when the bar is hidden. */
internal val LocalTabBarClearance = staticCompositionLocalOf { 0.dp }

/** The room a screen leaves under its last line so it scrolls clear of the floating tab bar (the kit's clearance). */
@Composable
internal fun TabBarClearance() {
    val clearance: Dp = LocalTabBarClearance.current
    if (clearance > 0.dp) Spacer(Modifier.height(clearance))
}
