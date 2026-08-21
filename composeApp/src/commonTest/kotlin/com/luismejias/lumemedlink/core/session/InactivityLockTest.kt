package com.luismejias.lumemedlink.core.session

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val WINDOW = 300_000L
private const val T0 = 1_000_000_000_000L

private class TestClock(var now: Long = T0) : Clock {
    override fun nowEpochMillis(): Long = now
}

class InactivityLockTest {

    @Test
    fun bornLocked() {
        val lock = InactivityLock(WINDOW, TestClock())
        assertTrue(lock.isLocked(), "fail closed: no recorded activity means locked")
    }

    @Test
    fun unlockOpensTheWindow() {
        val clock = TestClock()
        val lock = InactivityLock(WINDOW, clock)
        lock.unlock()
        assertFalse(lock.isLocked())

        clock.now = T0 + WINDOW - 1
        assertFalse(lock.isLocked())
    }

    @Test
    fun windowElapsesIntoLocked() {
        val clock = TestClock()
        val lock = InactivityLock(WINDOW, clock)
        lock.unlock()

        clock.now = T0 + WINDOW
        assertTrue(lock.isLocked())
    }

    @Test
    fun activitySlidesTheWindow() {
        val clock = TestClock()
        val lock = InactivityLock(WINDOW, clock)
        lock.unlock()

        clock.now = T0 + WINDOW - 1
        lock.recordActivity()
        clock.now = T0 + WINDOW + 1
        assertFalse(lock.isLocked(), "activity at T0+window-1 slid the deadline")
    }

    @Test
    fun activityOnALockedSessionDoesNotUnlock() {
        val clock = TestClock()
        val lock = InactivityLock(WINDOW, clock)
        lock.unlock()

        clock.now = T0 + WINDOW + 1
        lock.recordActivity()
        assertTrue(lock.isLocked(), "touching the screen is not re-authentication")
    }

    @Test
    fun resetReturnsToBornLocked() {
        val lock = InactivityLock(WINDOW, TestClock())
        lock.unlock()
        lock.reset()
        assertTrue(lock.isLocked())
    }
}
