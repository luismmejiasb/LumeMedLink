package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.ClinicClock
import com.luismejias.lumemedlink.shared.CalendarDate
import com.luismejias.lumemedlink.shared.ClockTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The clinic's zone until the contract publishes it (ADR-0063, backend request `0013`): the platform's own seed zone.
 * The one place that assumes a country; it goes when the field arrives.
 */
internal const val CLINIC_ZONE_UNTIL_CONTRACT = "America/Santiago"

/**
 * The zone the contract names for a place (`timeZone`, since 0.130.0 — backend request `0013`), or [fallback] when it
 * names none or one this platform does not know. The fallback is the constant above, and only the fallback.
 */
internal fun zoneOf(name: String?, fallback: TimeZone): TimeZone =
    name?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: fallback

/** One instant on a clinic's wall clock: its date and its hour, as the design draws them. */
internal data class WallTime(val date: CalendarDate, val time: ClockTime)

/** [instant], an ISO 8601 instant from the contract, on [zone]'s wall clock. Never the phone's (LumeMed's rule). */
internal fun wallTimeOf(instant: String, zone: TimeZone): WallTime = wallTimeOf(Instant.parse(instant), zone)

private fun wallTimeOf(instant: Instant, zone: TimeZone): WallTime {
    val local = instant.toLocalDateTime(zone)
    return WallTime(CalendarDate(local.year, local.month.ordinal + 1, local.day), ClockTime(local.hour, local.minute))
}

/** Whole minutes from [start] to [end], two ISO 8601 instants. */
internal fun minutesBetween(start: String, end: String): Int =
    (Instant.parse(end) - Instant.parse(start)).inWholeMinutes.toInt()

/**
 * [date] at [time] on [zone]'s wall clock, as the instant the contract expects in a request body: UTC, ending in `Z`,
 * with no offset (`rescheduleMyAppointment` answers 400 to `+00:00`).
 */
internal fun instantOf(date: CalendarDate, time: ClockTime, zone: TimeZone): String =
    LocalDateTime(date.year, date.month, date.day, time.hour, time.minute).toInstant(zone).toString()

/**
 * Every day of [zone]'s calendar that the period from [startsAt] to [endsAt] (exclusive) touches. Compared as
 * instants, never as text: the contract writes `…00.000Z` and this app `…00Z`, and as strings those order backwards.
 */
internal fun daysTouched(startsAt: String, endsAt: String, zone: TimeZone): List<CalendarDate> {
    val first = wallTimeOf(startsAt, zone).date
    val end = Instant.parse(endsAt)
    return generateSequence(first) { it.plusDays(1) }
        .takeWhile { it == first || LocalDateTime(it.year, it.month, it.day, 0, 0).toInstant(zone) < end }
        .toList()
}

/**
 * [minutes] after [instant], as an instant: a duration counts on the timeline, not on the wall clock, so an hour that
 * crosses a change of offset still lasts what it lasts.
 */
internal fun instantPlusMinutes(instant: String, minutes: Int): String =
    (Instant.parse(instant) + minutes.minutes).toString()

/**
 * Today and now on the clinic's wall clock. The instant is the device's, the only clock the app has; the calendar is
 * the clinic's, so a phone in another zone still sees the clinic's day (ADR-0063).
 */
internal class ClinicWallClock(
    private val zone: TimeZone = TimeZone.of(CLINIC_ZONE_UNTIL_CONTRACT),
    private val clock: Clock = Clock.System,
) : ClinicClock {
    override fun today(): CalendarDate = wallTimeOf(clock.now(), zone).date

    override fun now(): ClockTime = wallTimeOf(clock.now(), zone).time
}
