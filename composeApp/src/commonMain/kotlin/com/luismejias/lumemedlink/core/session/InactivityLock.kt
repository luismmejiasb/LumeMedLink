package com.luismejias.lumemedlink.core.session

/**
 * The inactivity lock's LOGIC (§8.3): a sliding window over injected clocks, testable without
 * timing. The biometric re-auth that answers it (tier 2 of ADR-0005) arrives with the shell —
 * this class only decides WHEN the gate must be shown.
 *
 * **TWO clocks, and the window closes when EITHER says it should** (ADR-0032). Each covers the
 * other's failure, and both failures are real:
 *
 * - The **wall clock** is a *setting*. Moving the phone's time backwards makes `now - last` go
 *   negative, so a window measured only that way never elapses — a lock anyone holding the device
 *   can switch off in Settings, on the shared-device threat this app ranks FIRST (§8.17). That was
 *   this class until 2026-09-21.
 * - The **elapsed clock** cannot be moved, but on some platforms it stops while the device sleeps,
 *   and a phone in a pocket for three hours is the ordinary case, not the exotic one.
 *
 * Taking the larger of the two elapsed values is fail-closed against both, and it does not require
 * being right about which platform pauses which clock. Winding the wall clock FORWARD locks
 * earlier, which is the harmless direction.
 *
 * Fail directions, per the family table:
 * - **Born locked**: `null` last-activity means locked. A fresh process with a persisted session
 *   re-authenticates; there is no grace window an attacker can race.
 * - **[recordActivity] cannot unlock.** Touching the screen after the window elapsed is not
 *   re-authentication; only [unlock] — called by the biometric gate or the login flow — restarts
 *   the window. Without this distinction, the lock would never fire on a phone in active use by
 *   the wrong person (§8.17: the shared-device threat this app ranks FIRST).
 *
 * Main-thread confined (§6): UI state, no internal synchronization on purpose.
 */
internal class InactivityLock(
    private val windowMillis: Long,
    private val clock: Clock = SystemClock,
    private val elapsedClock: ElapsedClock = SystemElapsedClock,
) {
    private var lastActivityAtMillis: Long? = null
    private var lastActivityElapsedMillis: Long = 0L

    fun isLocked(): Boolean = millisUntilLock() <= 0L

    /**
     * How long until this window closes, so the shell can SLEEP until then instead of polling —
     * and, more to the point, instead of never asking.
     *
     * The second half of the same defect: the shell only re-read [isLocked] from its pointer
     * handler, so the window could elapse with the agenda on screen and nothing would happen until
     * somebody touched the phone. The lock fired on the next touch, which is the one moment the
     * person is already looking at the screen (ADR-0032).
     *
     * Returns `0` when already locked, and never a negative number the caller could hand to
     * `delay()` and have it return immediately in a spin.
     */
    fun millisUntilLock(): Long {
        val lastWall = lastActivityAtMillis ?: return 0L
        val byWallClock = clock.nowEpochMillis() - lastWall
        val byElapsedClock = elapsedClock.elapsedMillis() - lastActivityElapsedMillis
        val elapsed = maxOf(byWallClock, byElapsedClock)
        val remaining = windowMillis - elapsed
        return if (remaining > 0L) remaining else 0L
    }

    /** Slides the window — only while still unlocked. */
    fun recordActivity() {
        if (isLocked()) return
        stamp()
    }

    /** Restarts the window after a successful re-auth (biometric gate) or login. */
    fun unlock() {
        stamp()
    }

    /** Part of logout: the next session starts locked. */
    fun reset() {
        lastActivityAtMillis = null
        lastActivityElapsedMillis = 0L
    }

    private fun stamp() {
        lastActivityAtMillis = clock.nowEpochMillis()
        lastActivityElapsedMillis = elapsedClock.elapsedMillis()
    }
}
