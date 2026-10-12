package com.luismejias.lumemedlink.features.patient.parts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cl.lume.uicomposer.components.LumeActivityIndicator
import cl.lume.uicomposer.components.LumeAvatar
import cl.lume.uicomposer.components.LumeAvatarSize
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerAlignment
import cl.lume.uicomposer.components.LumeEmptyState
import cl.lume.uicomposer.components.LumeListAccessory
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeStackAxis
import cl.lume.uicomposer.components.LumeTag
import cl.lume.uicomposer.components.LumeTagStyle
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTextStyle
import cl.lume.uicomposer.components.LumeTextWeight
import cl.lume.uicomposer.foundations.LumeIcon
import cl.lume.uicomposer.tokens.LumeTileTint
import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.CareDoctor
import com.luismejias.lumemedlink.core.care.CareFailure
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.care_loading
import com.luismejias.lumemedlink.resources.care_offline_body
import com.luismejias.lumemedlink.resources.care_offline_title
import com.luismejias.lumemedlink.resources.care_unavailable_body
import com.luismejias.lumemedlink.resources.care_unavailable_title
import com.luismejias.lumemedlink.resources.common_retry
import com.luismejias.lumemedlink.resources.date_day_month
import com.luismejias.lumemedlink.resources.date_long
import com.luismejias.lumemedlink.resources.date_short
import com.luismejias.lumemedlink.resources.date_today
import com.luismejias.lumemedlink.resources.date_tomorrow
import com.luismejias.lumemedlink.resources.date_weekday_day
import com.luismejias.lumemedlink.resources.months_long
import com.luismejias.lumemedlink.resources.months_short
import com.luismejias.lumemedlink.resources.status_cancelled
import com.luismejias.lumemedlink.resources.status_completed
import com.luismejias.lumemedlink.resources.status_confirmed
import com.luismejias.lumemedlink.resources.status_requested
import com.luismejias.lumemedlink.resources.weekdays_long
import com.luismejias.lumemedlink.resources.weekdays_short
import com.luismejias.lumemedlink.shared.CalendarDate
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** What a tab root holds while it reads: nothing yet, a failure with its retry, or the content. */
internal sealed interface CareLoad<out T> {
    data object Loading : CareLoad<Nothing>

    data class Failed(val failure: CareFailure) : CareLoad<Nothing>

    data class Ready<T>(val content: T) : CareLoad<T>
}

/** Turns a gateway answer into a [CareLoad]. */
internal fun <T, R> CareResult<T>.toLoad(transform: (T) -> R): CareLoad<R> = when (this) {
    is CareResult.Ok -> CareLoad.Ready(transform(value))
    is CareResult.Failed -> CareLoad.Failed(failure)
}

/** Several reads that must all succeed, or the first failure is what the screen shows. */
internal inline fun <R> allOf(vararg results: CareResult<*>, build: () -> R): CareLoad<R> =
    results.firstNotNullOfOrNull { (it as? CareResult.Failed)?.failure }
        ?.let { CareLoad.Failed(it) }
        ?: CareLoad.Ready(build())

/** Reads `value` of a result already checked by [allOf]. */
internal val <T> CareResult<T>.ok: T get() = (this as CareResult.Ok<T>).value

/** Sets a load flow to [CareLoad.Loading] unless it already holds content: a retry keeps what was shown. */
internal fun <T> MutableStateFlow<CareLoad<T>>.markLoading() {
    if (value !is CareLoad.Ready) value = CareLoad.Loading
}

/**
 * Renders the waiting and failed states of a read, and [content] once it is ready. [standalone] wraps the two
 * states in their own centred region (a tab root); inside a pushed screen they sit in the screen's region.
 * [offlineTitle] and [offlineBody] replace the generic words where the design names what failed (e6).
 */
@Composable
internal fun <T> CareLoadView(
    load: CareLoad<T>,
    onRetry: () -> Unit,
    standalone: Boolean = false,
    offlineTitle: StringResource = Res.string.care_offline_title,
    offlineBody: StringResource = Res.string.care_offline_body,
    content: @Composable (T) -> Unit,
) {
    val state: @Composable () -> Unit = {
        when (load) {
            CareLoad.Loading -> LumeActivityIndicator(contentDescription = stringResource(Res.string.care_loading))
            is CareLoad.Failed -> {
                val offline = load.failure == CareFailure.Offline
                LumeEmptyState(
                    icon = if (offline) LumeIcon.Wifi else LumeIcon.Alert,
                    title = stringResource(if (offline) offlineTitle else Res.string.care_unavailable_title),
                    message = stringResource(if (offline) offlineBody else Res.string.care_unavailable_body),
                    action = LumeButtonContent.Title(stringResource(Res.string.common_retry)),
                    onAction = onRetry,
                )
            }
            is CareLoad.Ready -> Unit
        }
    }
    when {
        load is CareLoad.Ready -> content(load.content)
        standalone -> LumeContainer(alignment = LumeContainerAlignment.Center) { state() }
        else -> LumeStack(modifier = Modifier.fillMaxWidth(), spacing = { medium }) { state() }
    }
}

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

/** The design's status words and their verdict; null for a status this app does not know. */
@Composable
internal fun statusLabel(status: AppointmentStatus): Pair<String, LumeTagStyle>? = when (status) {
    AppointmentStatus.CONFIRMED -> stringResource(Res.string.status_confirmed) to LumeTagStyle.Success
    AppointmentStatus.REQUESTED -> stringResource(Res.string.status_requested) to LumeTagStyle.Warning
    AppointmentStatus.COMPLETED -> stringResource(Res.string.status_completed) to LumeTagStyle.Neutral
    AppointmentStatus.CANCELLED -> stringResource(Res.string.status_cancelled) to LumeTagStyle.Error
    AppointmentStatus.OTHER -> null
}

/** The design's status capsule: green «Confirmada», amber «Por confirmar». */
@Composable
internal fun AppointmentStatusTag(status: AppointmentStatus) {
    statusLabel(status)?.let { (text, style) -> LumeTag(text = text, style = style) }
}

/**
 * A doctor's marker in a row: their initials on a disc whose colour is stable per doctor, silent because the row's
 * title already says the name.
 */
internal fun doctorMarker(doctor: CareDoctor): LumeListAccessory =
    LumeListAccessory.Avatar(doctor.initials, LumeTileTint.deterministic(doctor.id))

/**
 * A doctor as the design heads a card with them (p3, a1, a2): the initials on their stable colour, the name
 * stressed, each of [details] as a quieter line under it, and [extra] — the rating — last.
 */
@Composable
internal fun DoctorIdentity(
    doctor: CareDoctor,
    details: List<String>,
    avatar: LumeAvatarSize = LumeAvatarSize.Medium,
    extra: (@Composable () -> Unit)? = null,
) {
    LumeStack(axis = LumeStackAxis.Horizontal, alignment = LumeStackAlignment.Center) {
        LumeAvatar(
            initials = doctor.initials,
            size = avatar,
            tint = LumeTileTint.deterministic(doctor.id),
            contentDescription = doctor.displayName,
        )
        LumeStack(alignment = LumeStackAlignment.Start, spacing = { xSmall }) {
            LumeText(text = doctor.displayName, weight = LumeTextWeight.Strong)
            details.forEach { LumeText(text = it, style = LumeTextStyle.TextSmall, color = LumeTextColor.Secondary) }
            extra?.invoke()
        }
    }
}

/** First letter up, for a short date that opens a line: "Lun 21 sep · 09:00". */
internal fun String.capitalized(): String = replaceFirstChar { it.titlecase() }
