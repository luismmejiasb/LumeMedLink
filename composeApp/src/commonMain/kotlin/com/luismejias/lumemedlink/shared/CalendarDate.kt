package com.luismejias.lumemedlink.shared

/**
 * A day on the wall calendar of the place where the appointment happens — no time zone, no instant.
 *
 * The contract speaks in UTC instants; turning one into the day a patient reads needs the clinic's zone and a
 * time-zone database this app does not carry yet. That conversion belongs to the HTTP adapter when it exists
 * (with its dependency and ADR); the screens only ever see the wall-clock day.
 */
internal data class CalendarDate(val year: Int, val month: Int, val day: Int) : Comparable<CalendarDate> {
    init {
        require(month in 1..MONTHS && day in 1..daysIn(year, month)) { "not a calendar day" }
    }

    /** 1 = Monday … 7 = Sunday (ISO 8601). */
    val isoWeekday: Int get() = (epochDay() + EPOCH_ISO_WEEKDAY - 1).mod(WEEK.toLong()).toInt() + 1

    fun plusDays(days: Int): CalendarDate = fromEpochDay(epochDay() + days)

    fun daysUntil(other: CalendarDate): Int = (other.epochDay() - epochDay()).toInt()

    override fun compareTo(other: CalendarDate): Int = epochDay().compareTo(other.epochDay())

    /** Days since 1970-01-01, by the civil-calendar algorithm (H. Hinnant). */
    private fun epochDay(): Long {
        val y = if (month <= 2) year - 1 else year
        val era = y.floorDiv(YEARS_PER_ERA)
        val yearOfEra = y - era * YEARS_PER_ERA
        val shiftedMonth = (month + 9) % 12
        val dayOfYear = (153 * shiftedMonth + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era.toLong() * DAYS_PER_ERA + dayOfEra - EPOCH_SHIFT
    }

    companion object {
        private const val MONTHS = 12
        private const val WEEK = 7
        private const val YEARS_PER_ERA = 400
        private const val DAYS_PER_ERA = 146_097L
        private const val EPOCH_SHIFT = 719_468L

        /** 1970-01-01 was a Thursday. */
        private const val EPOCH_ISO_WEEKDAY = 4L

        fun daysIn(year: Int, month: Int): Int = when (month) {
            2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        private fun fromEpochDay(epochDay: Long): CalendarDate {
            val shifted = epochDay + EPOCH_SHIFT
            val era = shifted.floorDiv(DAYS_PER_ERA)
            val dayOfEra = shifted - era * DAYS_PER_ERA
            val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146_096) / 365
            val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
            val shiftedMonth = (5 * dayOfYear + 2) / 153
            val day = (dayOfYear - (153 * shiftedMonth + 2) / 5 + 1).toInt()
            val month = (if (shiftedMonth < 10) shiftedMonth + 3 else shiftedMonth - 9).toInt()
            val year = (yearOfEra + era * YEARS_PER_ERA).toInt() + if (month <= 2) 1 else 0
            return CalendarDate(year, month, day)
        }
    }
}

/** A time on the same wall clock, minute precision — what an appointment card reads. */
internal data class ClockTime(val hour: Int, val minute: Int) : Comparable<ClockTime> {
    init {
        require(hour in 0..LAST_HOUR && minute in 0..LAST_MINUTE) { "not a clock time" }
    }

    /** "09:00": 24-hour, as Chile writes it and as the design draws it. */
    val display: String get() = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    /** Minutes since midnight: what two clock times on one wall calendar subtract. */
    val minuteOfDay: Int get() = hour * MINUTES_PER_HOUR + minute

    override fun compareTo(other: ClockTime): Int = compareValuesBy(this, other, ClockTime::hour, ClockTime::minute)

    private companion object {
        const val LAST_HOUR = 23
        const val LAST_MINUTE = 59
        const val MINUTES_PER_HOUR = 60
    }
}
