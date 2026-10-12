package com.luismejias.lumemedlink.core.screen

import androidx.compose.runtime.Composable
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.date_day_month
import com.luismejias.lumemedlink.resources.date_long
import com.luismejias.lumemedlink.resources.date_short
import com.luismejias.lumemedlink.resources.date_today
import com.luismejias.lumemedlink.resources.date_tomorrow
import com.luismejias.lumemedlink.resources.date_weekday_day
import com.luismejias.lumemedlink.resources.months_long
import com.luismejias.lumemedlink.resources.months_short
import com.luismejias.lumemedlink.resources.weekdays_long
import com.luismejias.lumemedlink.resources.weekdays_short
import com.luismejias.lumemedlink.shared.CalendarDate
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** "Miércoles 16 de septiembre" / "Wednesday, September 16". */
@Composable
internal fun longDateText(date: CalendarDate): String = stringResource(
    Res.string.date_long,
    stringArrayResource(Res.array.weekdays_long)[date.isoWeekday - 1],
    date.day,
    stringArrayResource(Res.array.months_long)[date.month - 1],
)

/** "mié 16 sep" / "Wed, Sep 16". */
@Composable
internal fun shortDateText(date: CalendarDate): String = stringResource(
    Res.string.date_short,
    stringArrayResource(Res.array.weekdays_short)[date.isoWeekday - 1],
    date.day,
    stringArrayResource(Res.array.months_short)[date.month - 1],
)

/** "lun 21" — within the next days, where the month goes without saying. */
@Composable
internal fun weekdayDayText(date: CalendarDate): String = stringResource(
    Res.string.date_weekday_day,
    stringArrayResource(Res.array.weekdays_short)[date.isoWeekday - 1],
    date.day,
)

/** "Lunes 21" / "Monday 21": a day within the booking horizon, named in full. */
@Composable
internal fun longWeekdayDayText(date: CalendarDate): String = stringResource(
    Res.string.date_weekday_day,
    stringArrayResource(Res.array.weekdays_long)[date.isoWeekday - 1],
    date.day,
)

/** "2 sep" / "Sep 2". */
@Composable
internal fun dayMonthText(date: CalendarDate): String = stringResource(
    Res.string.date_day_month,
    date.day,
    stringArrayResource(Res.array.months_short)[date.month - 1],
)

/** "Hoy" or "Mañana" when it is one of those, else null. */
@Composable
internal fun relativeDayText(date: CalendarDate, today: CalendarDate): String? = when (today.daysUntil(date)) {
    0 -> stringResource(Res.string.date_today)
    1 -> stringResource(Res.string.date_tomorrow)
    else -> null
}

/** First letter up, for a short date that opens a line: "Lun 21 sep · 09:00". */
internal fun String.capitalized(): String = replaceFirstChar { it.titlecase() }
