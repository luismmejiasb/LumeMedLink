package com.luismejias.lumemedlink.core.networking

/**
 * Whether an engine's failure means the DEVICE has no network — not that the server refused, timed out or is down.
 * Only the platform's own error vocabulary can tell the two apart, so this is the one place each engine is asked; the
 * answer leaves as a boolean and the failure itself, which carries the URL, is dropped by the stack (ADR-0016).
 */
internal expect fun Throwable.meansNoNetwork(): Boolean
