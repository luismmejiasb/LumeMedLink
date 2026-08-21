package com.luismejias.lumemedlink.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

// Functional privacy cover, NOT a design token: an opaque fill whose only job is to hide content
// from the OS app-switcher snapshot and a momentary onlooker while the app is not in the
// foreground. Replaced by a theme surface when the design system lands (S0.3).
private val privacyCoverColor = Color(0xFF0E1116)

/**
 * Wraps [content] and drops an opaque cover over it whenever the app is not RESUMED (threat model
 * T2, ADR-0010). On iOS this is the PRIMARY screenshot protection — the platform offers no
 * FLAG_SECURE, so covering the view before the OS snapshots it for the app switcher is the
 * control. On Android FLAG_SECURE already blanks the thumbnail; here the cover is defense in depth.
 *
 * First layer, stated honestly: the robust iOS cover is a host UIWindow shown on the scene's
 * willResignActive, which needs the iOS shell that does not exist yet. This Compose overlay,
 * driven by the lifecycle, is the layer available without that host.
 */
@Composable
internal fun PrivacyScreenScaffold(content: @Composable () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var covered by remember { mutableStateOf(shouldCover(lifecycleOwner.lifecycle.currentState)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            covered = shouldCover(lifecycleOwner.lifecycle.currentState)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (covered) {
            Box(modifier = Modifier.fillMaxSize().background(privacyCoverColor))
        }
    }
}

/**
 * The cover is shown whenever the app is not actively in the foreground. RESUMED means on-screen
 * and interactive; every state below it is a moment the OS may snapshot or an onlooker may glimpse,
 * so it is covered. Total on purpose — the test pins every state, and "cover when unsure" is the
 * fail-closed direction for a screen-privacy control.
 */
internal fun shouldCover(state: Lifecycle.State): Boolean = state != Lifecycle.State.RESUMED
