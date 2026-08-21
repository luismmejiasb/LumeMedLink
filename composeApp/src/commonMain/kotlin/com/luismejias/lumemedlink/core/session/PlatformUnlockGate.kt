package com.luismejias.lumemedlink.core.session

import androidx.compose.runtime.Composable

/**
 * Builds the platform's tier-2 [UnlockGate] (ADR-0005/0011). Same seam shape as
 * [rememberSecureStore]: it lives in `core/` (ADR-0008) and is @Composable because the Android
 * implementation needs the hosting Activity, which Compose provides.
 *
 * Returns null when the platform cannot host a biometric gate at all (for example, an Android
 * host that is not a FragmentActivity). Null is not "unlocked": the caller treats a missing gate
 * as [UnlockOutcome.Unavailable], which ends the session — fail closed.
 */
@Composable
internal expect fun rememberUnlockGate(secureStore: SecureStore): UnlockGate?
