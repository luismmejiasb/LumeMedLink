package com.luismejias.lumemedlink.core.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val WINDOW = 300_000L
private const val T0 = 1_000_000_000_000L

/**
 * The two clocks move TOGETHER by default, because that is the ordinary world and every pre-existing
 * test describes it. The tests added in 2026-09-21 move them apart on purpose: that is where the
 * defect lived, and where an injected clock earns its keep (ADR-0032).
 */
private class TestClock(var now: Long = T0) : Clock {
    override fun nowEpochMillis(): Long = now
}

private class TestElapsedClock(var elapsed: Long = 0L) : ElapsedClock {
    override fun elapsedMillis(): Long = elapsed
}

/** A wall clock and an elapsed clock wired to the same knob. */
private class Clocks(private val start: Long = T0) {
    val wall = TestClock(start)
    val elapsed = TestElapsedClock(0L)

    fun advance(millis: Long) {
        wall.now += millis
        elapsed.elapsed += millis
    }
}

private fun lockOf(clocks: Clocks) = InactivityLock(WINDOW, clocks.wall, clocks.elapsed)

class InactivityLockTest {

    @Test
    fun bornLocked() {
        val lock = InactivityLock(WINDOW, TestClock(), TestElapsedClock())
        assertTrue(lock.isLocked(), "fail closed: no recorded activity means locked")
    }

    @Test
    fun unlockOpensTheWindow() {
        val clock = TestClock()
        val elapsedClock = TestElapsedClock()
        val lock = InactivityLock(WINDOW, clock, elapsedClock)
        lock.unlock()
        assertFalse(lock.isLocked())

        clock.now = T0 + WINDOW - 1

        elapsedClock.elapsed = WINDOW - 1
        assertFalse(lock.isLocked())
    }

    @Test
    fun windowElapsesIntoLocked() {
        val clock = TestClock()
        val elapsedClock = TestElapsedClock()
        val lock = InactivityLock(WINDOW, clock, elapsedClock)
        lock.unlock()

        clock.now = T0 + WINDOW

        elapsedClock.elapsed = WINDOW
        assertTrue(lock.isLocked())
    }

    @Test
    fun activitySlidesTheWindow() {
        val clock = TestClock()
        val elapsedClock = TestElapsedClock()
        val lock = InactivityLock(WINDOW, clock, elapsedClock)
        lock.unlock()

        clock.now = T0 + WINDOW - 1

        elapsedClock.elapsed = WINDOW - 1
        lock.recordActivity()
        clock.now = T0 + WINDOW + 1
        elapsedClock.elapsed = WINDOW + 1
        assertFalse(lock.isLocked(), "activity at T0+window-1 slid the deadline")
    }

    @Test
    fun activityOnALockedSessionDoesNotUnlock() {
        val clock = TestClock()
        val elapsedClock = TestElapsedClock()
        val lock = InactivityLock(WINDOW, clock, elapsedClock)
        lock.unlock()

        clock.now = T0 + WINDOW + 1

        elapsedClock.elapsed = WINDOW + 1
        lock.recordActivity()
        assertTrue(lock.isLocked(), "touching the screen is not re-authentication")
    }

    @Test
    fun resetReturnsToBornLocked() {
        val lock = InactivityLock(WINDOW, TestClock(), TestElapsedClock())
        lock.unlock()
        lock.reset()
        assertTrue(lock.isLocked())
    }

    // ── Lo que la auditoría encontró (ADR-0032) ─────────────────────────────────────────────────

    @Test
    fun windingTheWallClockBackDoesNotKeepTheSessionOpen() {
        // THE DEFECT. The wall clock is a SETTING. With the window measured only by it,
        // `now - last` went negative and the session stayed open forever — a lock that anyone
        // holding the phone could switch off in Settings, on the threat this app ranks first.
        val clocks = Clocks()
        val lock = lockOf(clocks)
        lock.unlock()

        clocks.elapsed.elapsed += WINDOW // the real, unmovable elapsed time
        clocks.wall.now -= 24 * 60 * 60 * 1000L // the phone's time, moved a day into the past

        assertTrue(lock.isLocked(), "moving the clock backwards kept the session unlocked")
    }

    @Test
    fun anElapsedClockThatStopsDoesNotKeepTheSessionOpenEither() {
        // The other half, and the reason the rule is "either clock", not "the monotonic one":
        // on some platforms the elapsed clock stops while the device sleeps, and a phone in a
        // pocket is the ordinary case. The wall clock covers that.
        val clocks = Clocks()
        val lock = lockOf(clocks)
        lock.unlock()

        clocks.wall.now += WINDOW // hours passed on the wall
        // and the elapsed clock did not move at all, as if the device had been asleep

        assertTrue(lock.isLocked(), "a paused elapsed clock kept the session unlocked")
    }

    @Test
    fun millisUntilLockCountsDownAndNeverGoesNegative() {
        // What the shell sleeps on. A negative number handed to delay() returns immediately and
        // spins; zero means "already locked", which the caller must be able to tell.
        val clocks = Clocks()
        val lock = lockOf(clocks)
        lock.unlock()
        assertEquals(WINDOW, lock.millisUntilLock())

        clocks.advance(WINDOW / 2)
        assertEquals(WINDOW / 2, lock.millisUntilLock())

        clocks.advance(WINDOW * 10)
        assertEquals(0L, lock.millisUntilLock(), "must clamp at zero, never hand delay() a negative")
    }

    @Test
    fun millisUntilLockIsZeroWhenBornLocked() {
        assertEquals(0L, lockOf(Clocks()).millisUntilLock())
    }

    @Test
    fun theStricterOfTheTwoClocksWins() {
        // Neither clock alone has elapsed; together neither has either. The window holds.
        val clocks = Clocks()
        val lock = lockOf(clocks)
        lock.unlock()
        clocks.wall.now += WINDOW - 1
        clocks.elapsed.elapsed += WINDOW - 1
        assertFalse(lock.isLocked())

        // Now only ONE of them crosses the line. That is enough.
        clocks.elapsed.elapsed += 1
        assertTrue(lock.isLocked(), "the window must close when EITHER clock says it elapsed")
    }
}
