package com.luismejias.lumemedlink.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CalendarDateTest {
    @Test
    fun theDesignsDayIsAWednesday() {
        assertEquals(3, CalendarDate(2026, 9, 16).isoWeekday)
        assertEquals(1, CalendarDate(2026, 9, 21).isoWeekday)
        assertEquals(4, CalendarDate(1970, 1, 1).isoWeekday)
        assertEquals(7, CalendarDate(2024, 2, 25).isoWeekday)
    }

    @Test
    fun addingDaysCrossesMonthsYearsAndLeapDays() {
        assertEquals(CalendarDate(2026, 10, 1), CalendarDate(2026, 9, 30).plusDays(1))
        assertEquals(CalendarDate(2027, 1, 1), CalendarDate(2026, 12, 31).plusDays(1))
        assertEquals(CalendarDate(2024, 2, 29), CalendarDate(2024, 2, 28).plusDays(1))
        assertEquals(CalendarDate(2026, 9, 2), CalendarDate(2026, 9, 16).plusDays(-14))
        assertEquals(CalendarDate(1969, 12, 31), CalendarDate(1970, 1, 1).plusDays(-1))
    }

    @Test
    fun everyDayOfFourCenturiesRoundTrips() {
        var day = CalendarDate(1900, 1, 1)
        repeat(146_097) {
            val next = day.plusDays(1)
            assertEquals(1, day.daysUntil(next))
            assertTrue(next > day)
            assertEquals(day.isoWeekday % 7 + 1, next.isoWeekday)
            day = next
        }
        assertEquals(CalendarDate(2300, 1, 1), day)
    }

    @Test
    fun aDayThatDoesNotExistIsRefused() {
        assertFailsWith<IllegalArgumentException> { CalendarDate(2026, 2, 29) }
        assertFailsWith<IllegalArgumentException> { CalendarDate(2026, 13, 1) }
        assertFailsWith<IllegalArgumentException> { ClockTime(24, 0) }
    }

    @Test
    fun aTimeReadsOnTheTwentyFourHourClock() {
        assertEquals("09:00", ClockTime(9, 0).display)
        assertEquals("16:05", ClockTime(16, 5).display)
    }

    @Test
    fun aMobileReadsInFull() {
        assertEquals("+56 9 8765 4321", PhoneNumber.chileanMobile("987654321")?.display)
    }
}
