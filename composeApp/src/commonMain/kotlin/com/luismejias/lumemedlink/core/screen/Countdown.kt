package com.luismejias.lumemedlink.core.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.luismejias.lumemedlink.core.session.Clock
import kotlinx.coroutines.delay

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L

/**
 * Whole seconds until [untilEpochMillis] on [clock], ticking once a second and stopping at zero. `null` in, `null`
 * out. Read from the clock every tick rather than counted down, so a rotation or a slow frame cannot drift it.
 */
@Composable
internal fun secondsUntil(untilEpochMillis: Long?, clock: Clock): Long? {
    if (untilEpochMillis == null) return null
    fun remaining() =
        ((untilEpochMillis - clock.nowEpochMillis()) + MILLIS_PER_SECOND - 1).coerceAtLeast(0) / MILLIS_PER_SECOND
    var seconds by remember(untilEpochMillis) { mutableLongStateOf(remaining()) }
    LaunchedEffect(untilEpochMillis) {
        while (seconds > 0) {
            delay(MILLIS_PER_SECOND)
            seconds = remaining()
        }
    }
    return seconds
}

/** "14:32" — minutes and seconds, the design's countdown format in both languages. */
internal fun clockText(seconds: Long): String {
    val minutes = seconds / SECONDS_PER_MINUTE
    val rest = seconds % SECONDS_PER_MINUTE
    return "$minutes:${rest.toString().padStart(2, '0')}"
}
