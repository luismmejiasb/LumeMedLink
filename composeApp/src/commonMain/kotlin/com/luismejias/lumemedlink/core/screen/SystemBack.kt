package com.luismejias.lumemedlink.core.screen

import androidx.compose.runtime.Composable

/**
 * The platform's back — Android's back button or gesture — runs [onBack], the same move as the ‹ the screen draws.
 * Without it Android's back closed the whole app from any screen, since nothing in the app answered it (found by the
 * author on the emulator, 2026-10-13). `null` lets the platform have it: on a root screen, back leaves the app.
 *
 * It is called where the ‹ is drawn (`AccessBack`, `PushedScreen`), so a screen with a way back always answers both.
 * The platform's half is an actual: Android's dispatcher lives in the activity, iOS's in Compose's own handler.
 */
@Composable
internal expect fun SystemBack(onBack: (() -> Unit)?)
