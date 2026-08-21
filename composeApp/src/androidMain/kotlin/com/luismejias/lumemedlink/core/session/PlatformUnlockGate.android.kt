package com.luismejias.lumemedlink.core.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity

@Composable
internal actual fun rememberUnlockGate(secureStore: SecureStore): UnlockGate? {
    // BiometricPrompt needs a FragmentActivity host. If the shell ever stops providing one, the
    // gate is absent rather than degraded — and an absent gate ends the session (fail closed),
    // it never means "unlocked".
    val activity = LocalContext.current as? FragmentActivity ?: return null
    return remember(activity, secureStore) { BiometricUnlockGate(activity, secureStore) }
}
