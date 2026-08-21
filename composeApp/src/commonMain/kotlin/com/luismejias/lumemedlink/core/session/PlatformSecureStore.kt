package com.luismejias.lumemedlink.core.session

import androidx.compose.runtime.Composable

/**
 * Constructs the platform's tier-1 [SecureStore] (ADR-0005/0009). Lives in core/ as expect/actual
 * (ADR-0008) because it is a platform seam; it is @Composable because the Android store needs a
 * Context that Compose provides through `LocalContext`, the idiomatic CMP construction point. This
 * is the one place core/session touches Compose — a construction seam, not a UI concern.
 */
@Composable
internal expect fun rememberSecureStore(): SecureStore
