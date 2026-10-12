package com.luismejias.lumemedlink.app

import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * The iOS half of the shell: what the Xcode host embeds. Everything it shows comes from
 * `commonMain`. Lives in the iOS source set of `core`-less `app/` because it is composition,
 * not a platform capability (ADR-0008 keeps expect/actual seams in `core/`; this is neither —
 * it is the platform's entry point).
 */
// PascalCase kept on purpose: this is the name Xcode consumes (`MainViewControllerKt`), and the
// Compose Multiplatform convention for the iOS entry point.
//
// The keyboard is the kit's to handle: its containers pad the pinned action by the keyboard and bring a focused field
// into view. Compose's default also shifts the whole view up on focus, which counted the keyboard twice and left 2c's
// lines under «Crear cuenta». So the view itself does nothing on focus.
@Suppress("ktlint:standard:function-naming")
public fun MainViewController(): UIViewController = ComposeUIViewController(
    configure = { onFocusBehavior = OnFocusBehavior.DoNothing },
) { App() }
