package com.luismejias.lumemedlink.core.session

internal actual fun epochMillisNow(): Long = System.currentTimeMillis()

/**
 * `elapsedRealtime`, not `uptimeMillis` and not `nanoTime`: it is the only one of the three that
 * keeps counting while the device is in deep sleep, and a phone left in a pocket is the ordinary
 * case for an inactivity window. None of the three is settable by the user, which is the other half
 * of what this clock is for (ADR-0032).
 */
internal actual fun elapsedMillisNow(): Long = android.os.SystemClock.elapsedRealtime()
