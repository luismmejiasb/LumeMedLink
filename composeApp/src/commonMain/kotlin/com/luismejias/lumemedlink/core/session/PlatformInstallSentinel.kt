package com.luismejias.lumemedlink.core.session

/**
 * Constructs the platform's [InstallSentinel] (F7, ADR-0028). A `core/` seam like
 * [rememberSecureStore], and deliberately NOT `@Composable`: neither platform needs anything
 * Compose provides, and the boundary must be enforceable from any launch path — including ones
 * that never compose.
 */
internal expect fun platformInstallSentinel(): InstallSentinel
