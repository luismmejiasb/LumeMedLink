package com.luismejias.lumemedlink.app

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * The iOS half of the shell: what the Xcode host embeds. Everything it shows comes from
 * `commonMain`. Lives in the iOS source set of `core`-less `app/` because it is composition,
 * not a platform capability (ADR-0008 keeps expect/actual seams in `core/`; this is neither —
 * it is the platform's entry point).
 */
public fun MainViewController(): UIViewController = ComposeUIViewController { App() }
