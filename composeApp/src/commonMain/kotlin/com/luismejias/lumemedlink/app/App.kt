package com.luismejias.lumemedlink.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Root composable of the app — the single entry point both platform shells render.
 *
 * `public` on purpose: this is the Gradle-module boundary (§3) that `:androidApp` and the iOS
 * framework consume. Everything else in this tree starts `internal`.
 *
 * Placeholder content only: the real shell arrives with S1.1, styled through the design system
 * once S0.3 is unblocked. No design tokens exist yet, so nothing here may claim a style.
 */
@Composable
public fun App() {
    // Every screen renders inside the privacy scaffold (ADR-0010): the cover is the app's, not
    // each screen's, so no screen can be built without it.
    PrivacyScreenScaffold {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BasicText(text = "LumeMedLink")
        }
    }
}
