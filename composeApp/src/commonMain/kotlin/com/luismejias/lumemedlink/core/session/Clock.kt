package com.luismejias.lumemedlink.core.session

/** Injectable time (§6: testable without timing). Production wires [SystemClock]. */
internal fun interface Clock {
    fun nowEpochMillis(): Long
}

internal object SystemClock : Clock {
    override fun nowEpochMillis(): Long = epochMillisNow()
}

/** Platform wall clock (ADR-0008: expect/actual lives in core/ only). */
internal expect fun epochMillisNow(): Long

/**
 * Elapsed time the user cannot move, for measuring a DURATION rather than naming an instant.
 *
 * The distinction is not pedantry, it is the defect this seam was added for (ADR-0032). The
 * inactivity window measured itself with the wall clock, and the wall clock is a **setting**: move
 * the phone's time backwards and `now - lastActivity` goes negative, so the window never elapses
 * and the session never locks. On the shared device §8.17 ranks first, that is a lock anyone
 * holding the phone can switch off in Settings.
 *
 * Both platforms are asked for the same thing and it is spelled out rather than inherited from a
 * default: time since boot **including the time the device spent asleep**. Excluding sleep would
 * fail in the other direction and far more often — a phone in a pocket for three hours would come
 * back unlocked.
 */
internal fun interface ElapsedClock {
    fun elapsedMillis(): Long
}

internal object SystemElapsedClock : ElapsedClock {
    override fun elapsedMillis(): Long = elapsedMillisNow()
}

/** Platform elapsed-since-boot clock, sleep included (ADR-0008: expect/actual lives in core/). */
internal expect fun elapsedMillisNow(): Long
