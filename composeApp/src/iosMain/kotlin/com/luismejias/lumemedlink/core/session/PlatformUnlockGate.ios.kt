package com.luismejias.lumemedlink.core.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberUnlockGate(secureStore: SecureStore): UnlockGate? =
    // The iOS gate owns its own Keychain item (its access control is incompatible with the tier-1
    // store's accessibility attribute), so it takes no store — the parameter is the common seam's.
    remember { KeychainUnlockGate() }
