package com.luismejias.lumemedlink.core.session

/**
 * The inactivity lock's LOGIC (§8.3): a sliding window over an injected clock, testable without
 * timing. The biometric re-auth that answers it (tier 2 of ADR-0005) arrives with the shell —
 * this class only decides WHEN the gate must be shown.
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
internal class InactivityLock(private val windowMillis: Long, private val clock: Clock = SystemClock) {
    private var lastActivityAtMillis: Long? = null

    fun isLocked(): Boolean {
        val last = lastActivityAtMillis ?: return true
        return clock.nowEpochMillis() - last >= windowMillis
    }

    /** Slides the window — only while still unlocked. */
    fun recordActivity() {
        if (isLocked()) return
        lastActivityAtMillis = clock.nowEpochMillis()
    }

    /** Restarts the window after a successful re-auth (biometric gate) or login. */
    fun unlock() {
        lastActivityAtMillis = clock.nowEpochMillis()
    }

    /** Part of logout: the next session starts locked. */
    fun reset() {
        lastActivityAtMillis = null
    }
}
