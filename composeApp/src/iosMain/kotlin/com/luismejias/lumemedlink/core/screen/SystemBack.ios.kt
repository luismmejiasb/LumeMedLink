package com.luismejias.lumemedlink.core.screen

import androidx.compose.runtime.Composable

/**
 * Nothing to answer: iOS has no system back button. Its back is the edge swipe, which the kit offers as
 * `Modifier.lumeSwipeBack` and this app does not wire yet — a decision of its own, not this function's. Compose's
 * common `BackHandler` is deprecated for `NavigationEventHandler`, which would be a new dependency for a back iOS does
 * not send.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
internal actual fun SystemBack(onBack: (() -> Unit)?) = Unit
