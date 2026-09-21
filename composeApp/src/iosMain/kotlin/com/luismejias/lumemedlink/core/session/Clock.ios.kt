package com.luismejias.lumemedlink.core.session

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.posix.CLOCK_MONOTONIC
import platform.posix.clock_gettime
import platform.posix.timespec

internal actual fun epochMillisNow(): Long = (NSDate().timeIntervalSince1970() * 1000).toLong()

/**
 * `CLOCK_MONOTONIC`, which on Darwin is documented as continuing to increment **while the system is
 * asleep** — the opposite of `CLOCK_UPTIME_RAW` and of `ProcessInfo.systemUptime`, which stop. That
 * makes it the equivalent of Android's `elapsedRealtime`, which is what this seam asks for.
 *
 * **Honest about the evidence:** the sleep behaviour is read from Apple's `clock_gettime`
 * documentation, not measured — a simulator does not sleep, and this session had no device to sleep.
 * The direction of the risk if the documentation were wrong is the SAFE one anyway: the lock would
 * fall back to the wall-clock half of [InactivityLock], which is where it was before (ADR-0032).
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun elapsedMillisNow(): Long = memScoped {
    val now = alloc<timespec>()
    clock_gettime(CLOCK_MONOTONIC.toUInt(), now.ptr)
    now.tv_sec * 1_000L + now.tv_nsec / 1_000_000L
}
