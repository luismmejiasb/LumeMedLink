package com.luismejias.lumemedlink.core.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import cl.lume.uicomposer.foundations.LocalLumeClipboardPolicy
import cl.lume.uicomposer.foundations.LumeClipboardPolicy

/**
 * What is copied or cut inside a field stays on this phone and expires (ADR-0042, task 0022, the author's
 * option B): every kit field below copies through the kit's `LocalExpiring` policy — on iOS the general
 * pasteboard with `localOnly` (no Universal Clipboard to the person's Mac or iPad) and a 2-minute expiry.
 * Pasting is unchanged. Android has no API for either, so there it is the system clipboard, as ADR-0013
 * declares.
 *
 * Here and not in a screen, for the reason ADR-0013 gives: the field decisions live in `core/input`. The
 * composition root wraps the whole app in it once; `Scripts/check-input-surfaces.sh` refuses the app
 * without it.
 */
@Composable
internal fun FieldClipboardPolicy(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLumeClipboardPolicy provides LumeClipboardPolicy.LocalExpiring, content = content)
}
