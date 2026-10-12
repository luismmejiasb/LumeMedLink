package com.luismejias.lumemedlink.features.patient.change

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cl.lume.uicomposer.components.LumeAvatar
import cl.lume.uicomposer.components.LumeAvatarSize
import cl.lume.uicomposer.components.LumeButton
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeButtonEmphasis
import cl.lume.uicomposer.components.LumeButtonRole
import cl.lume.uicomposer.components.LumeButtonSize
import cl.lume.uicomposer.components.LumeButtonWidth
import cl.lume.uicomposer.components.LumeCallout
import cl.lume.uicomposer.components.LumeCalloutStyle
import cl.lume.uicomposer.components.LumeCalloutSurface
import cl.lume.uicomposer.components.LumeCard
import cl.lume.uicomposer.components.LumeCardStyle
import cl.lume.uicomposer.components.LumeEyebrow
import cl.lume.uicomposer.components.LumeList
import cl.lume.uicomposer.components.LumeListAccessory
import cl.lume.uicomposer.components.LumeListChrome
import cl.lume.uicomposer.components.LumeListDensity
import cl.lume.uicomposer.components.LumeListIconInk
import cl.lume.uicomposer.components.LumeListRow
import cl.lume.uicomposer.components.LumeListRowTextStyle
import cl.lume.uicomposer.components.LumeRatingInput
import cl.lume.uicomposer.components.LumeRatingInputSize
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeStackAxis
import cl.lume.uicomposer.components.LumeStackStyle
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextAlignment
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTextStyle
import cl.lume.uicomposer.components.LumeTextWeight
import cl.lume.uicomposer.foundations.LumeIcon
import cl.lume.uicomposer.tokens.LumeTileTint
import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.SentRating
import com.luismejias.lumemedlink.features.patient.parts.AppointmentStatusTag
import com.luismejias.lumemedlink.features.patient.parts.CareLoad
import com.luismejias.lumemedlink.features.patient.parts.CareLoadView
import com.luismejias.lumemedlink.features.patient.parts.PushedScreen
import com.luismejias.lumemedlink.features.patient.parts.dayMonthText
import com.luismejias.lumemedlink.features.patient.parts.longDateText
import com.luismejias.lumemedlink.features.patient.parts.relativeDayText
import com.luismejias.lumemedlink.features.patient.parts.richRuns
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.confirm_place
import com.luismejias.lumemedlink.resources.confirm_when
import com.luismejias.lumemedlink.resources.detail_arrive
import com.luismejias.lumemedlink.resources.detail_arrive_late
import com.luismejias.lumemedlink.resources.detail_book_again
import com.luismejias.lumemedlink.resources.detail_cancel
import com.luismejias.lumemedlink.resources.detail_change
import com.luismejias.lumemedlink.resources.detail_clinical
import com.luismejias.lumemedlink.resources.detail_contact
import com.luismejias.lumemedlink.resources.detail_pending
import com.luismejias.lumemedlink.resources.detail_rating
import com.luismejias.lumemedlink.resources.detail_rating_sent
import com.luismejias.lumemedlink.resources.detail_time_range
import com.luismejias.lumemedlink.resources.detail_title
import com.luismejias.lumemedlink.resources.detail_withdraw
import com.luismejias.lumemedlink.resources.doctors_call
import com.luismejias.lumemedlink.resources.home_directions
import com.luismejias.lumemedlink.resources.rate_label
import com.luismejias.lumemedlink.resources.rate_none
import com.luismejias.lumemedlink.resources.rate_value
import com.luismejias.lumemedlink.resources.status_completed
import com.luismejias.lumemedlink.shared.CalendarDate
import com.luismejias.lumemedlink.shared.ClockTime
import org.jetbrains.compose.resources.stringResource

private val UPCOMING = setOf(AppointmentStatus.CONFIRMED, AppointmentStatus.REQUESTED)

private const val MAX_STARS = 5

/**
 * c1–c3 · Detalle de la cita. Never a diagnosis, a prescription or a clinical note — there are none to show
 * (ADR-0001), and c2's «Motivo que escribiste» is not here for the same reason. PROVISIONAL (ADR-0046): no «Previsión»,
 * the contract books without one. Past the doctor's window, «Cambiar hora» and «Cancelar» open f2, which offers the
 * practice's phone.
 */
@Composable
internal fun AppointmentDetailScreen(
    model: ChangeViewModel,
    today: CalendarDate,
    onDirections: (PatientAppointment) -> Unit,
    onCall: (String) -> Unit,
    onBookAgain: (PatientAppointment) -> Unit,
) {
    val load by model.detail.state.collectAsState()
    val ready = (load as? CareLoad.Ready)?.content
    PushedScreen(
        title = stringResource(Res.string.detail_title),
        onBack = model::back,
        action = { ready?.let { DetailActions(it, model, onBookAgain) } },
    ) {
        CareLoadView(load, onRetry = {
            ready?.appointment?.let { appointment -> model.launch { detail.load(appointment) } }
        }) { content ->
            val appointment = content.appointment
            val upcoming = appointment.status in UPCOMING
            DetailHeader(appointment)
            if (appointment.status == AppointmentStatus.REQUESTED) {
                LumeCallout(
                    runs = stringResource(Res.string.detail_pending, appointment.doctor.displayName).richRuns(),
                    style = LumeCalloutStyle.Warning,
                    surface = LumeCalloutSurface.Washed,
                    icon = LumeIcon.Bell,
                )
            }
            DetailFacts(content, today, upcoming, onDirections = { onDirections(appointment) }, onCall = onCall)
            if (upcoming) {
                LumeCallout(
                    runs = if (content.options.canChange) {
                        stringResource(Res.string.detail_arrive, windowText(content.options.cutoffMinutes))
                    } else {
                        stringResource(Res.string.detail_arrive_late)
                    }.richRuns(),
                    style = LumeCalloutStyle.Info,
                    surface = LumeCalloutSurface.Washed,
                )
            }
            if (appointment.status == AppointmentStatus.COMPLETED) {
                LumeEyebrow(stringResource(Res.string.detail_rating))
                RatingCard(content.rating)
                LumeCallout(runs = stringResource(Res.string.detail_clinical).richRuns())
            }
        }
    }
}

@Composable
private fun DetailHeader(appointment: PatientAppointment) {
    LumeStack(modifier = Modifier.fillMaxWidth(), alignment = LumeStackAlignment.Center, spacing = { xSmall }) {
        LumeAvatar(
            initials = appointment.doctor.initials,
            size = LumeAvatarSize.Large,
            tint = LumeTileTint.deterministic(appointment.doctor.id),
            contentDescription = appointment.doctor.displayName,
        )
        LumeText(
            text = appointment.doctor.displayName,
            style = LumeTextStyle.HeadingMedium,
            alignment = LumeTextAlignment.Center,
        )
        LumeText(text = appointment.doctor.specialty, color = LumeTextColor.Secondary)
        if (appointment.status == AppointmentStatus.COMPLETED) {
            // c3 says it in plain words, not as a pill.
            LumeText(
                text = stringResource(Res.string.status_completed),
                style = LumeTextStyle.TextSmall,
                color = LumeTextColor.Secondary,
                weight = LumeTextWeight.Strong,
            )
        } else {
            AppointmentStatusTag(appointment.status)
        }
    }
}

private enum class Fact { WHEN, WHERE, CONTACT }

@Composable
private fun DetailFacts(
    content: DetailContent,
    today: CalendarDate,
    upcoming: Boolean,
    onDirections: () -> Unit,
    onCall: (String) -> Unit,
) {
    val appointment = content.appointment
    val phone = content.phone?.takeIf { upcoming }
    val facts = listOfNotNull(Fact.WHEN, Fact.WHERE, phone?.let { Fact.CONTACT })
    val day = relativeDayText(appointment.date, today)
        ?.let { "$it · ${longDateText(appointment.date).replaceFirstChar { c -> c.lowercase() }}" }
        ?: longDateText(appointment.date)
    val range = stringResource(
        Res.string.detail_time_range,
        appointment.start.display,
        appointment.start.plusMinutes(appointment.minutes).display,
    )
    LumeList(
        items = facts,
        key = { it },
        density = LumeListDensity.Compact,
        scrolls = false,
        background = Color.Transparent,
        chrome = LumeListChrome.Open,
    ) { fact ->
        when (fact) {
            Fact.WHEN -> LumeListRow(
                title = stringResource(Res.string.confirm_when),
                description = "$day\n$range",
                leading = LumeListAccessory.Icon(LumeIcon.Calendar, "", ink = LumeListIconInk.Accent),
                textStyle = LumeListRowTextStyle.LabelOverValue,
            )
            Fact.WHERE -> LumeListRow(
                title = stringResource(Res.string.confirm_place),
                description = "${appointment.place.address}\n${appointment.place.detail}",
                leading = LumeListAccessory.Icon(LumeIcon.Pin, "", ink = LumeListIconInk.Accent),
                textStyle = LumeListRowTextStyle.LabelOverValue,
            ) {
                if (upcoming) {
                    LumeButton(
                        content = LumeButtonContent.LeadingIcon(
                            LumeIcon.Directions,
                            stringResource(Res.string.home_directions),
                        ),
                        emphasis = LumeButtonEmphasis.Tonal,
                        size = LumeButtonSize.Card,
                        onClick = onDirections,
                    )
                }
            }
            Fact.CONTACT -> LumeListRow(
                title = stringResource(Res.string.detail_contact),
                description = phone,
                leading = LumeListAccessory.Icon(LumeIcon.Phone, "", ink = LumeListIconInk.Accent),
                textStyle = LumeListRowTextStyle.LabelOverValue,
            ) {
                LumeButton(
                    content = LumeButtonContent.LeadingIcon(LumeIcon.Phone, stringResource(Res.string.doctors_call)),
                    emphasis = LumeButtonEmphasis.Tonal,
                    size = LumeButtonSize.Card,
                    onClick = { phone?.let(onCall) },
                )
            }
        }
    }
}

/** c3: the stars sent, read only, the patient's words and when; unrated, the stars that open f5. */
@Composable
private fun RatingCard(rating: SentRating?) {
    val spoken = listOf(stringResource(Res.string.rate_none)) +
        (1..MAX_STARS).map { stringResource(Res.string.rate_value, it) }
    LumeCard(modifier = Modifier.fillMaxWidth(), style = LumeCardStyle.Filled) {
        LumeStack(alignment = LumeStackAlignment.Start, spacing = { small }) {
            val label = stringResource(Res.string.rate_label)
            val describe: (Int) -> String = { spoken.getOrElse(it) { spoken.first() } }
            if (rating != null) {
                LumeRatingInput(rating.stars, label, describe, size = LumeRatingInputSize.Compact)
            } else {
                // f5 waits for its own slice; the stars are drawn as c3 asks and open nothing yet.
                LumeRatingInput(0, onValueChange = {}, label, describe, size = LumeRatingInputSize.Compact)
            }
            rating?.comment?.let { LumeText(text = "«$it»") }
            rating?.let {
                LumeText(
                    text = stringResource(Res.string.detail_rating_sent, dayMonthText(it.sentOn)),
                    style = LumeTextStyle.TextSmall,
                    color = LumeTextColor.Secondary,
                )
            }
        }
    }
}

@Composable
private fun DetailActions(content: DetailContent, model: ChangeViewModel, onBookAgain: (PatientAppointment) -> Unit) {
    val appointment = content.appointment
    if (appointment.status !in UPCOMING) {
        LumeButton(
            content = LumeButtonContent.Title(stringResource(Res.string.detail_book_again)),
            size = LumeButtonSize.Bar,
            width = LumeButtonWidth.Fill,
            onClick = { onBookAgain(appointment) },
        )
        return
    }
    val options = content.options
    LumeStack(axis = LumeStackAxis.Horizontal, style = LumeStackStyle.Paired) {
        LumeButton(
            content = LumeButtonContent.Title(
                stringResource(
                    if (appointment.status == AppointmentStatus.REQUESTED) {
                        Res.string.detail_withdraw
                    } else {
                        Res.string.detail_cancel
                    },
                ),
            ),
            emphasis = LumeButtonEmphasis.Outline,
            role = LumeButtonRole.Destructive,
            size = LumeButtonSize.Bar,
            width = LumeButtonWidth.Fill,
            onClick = {
                if (options.canCancel) {
                    model.cancelAppointment(appointment)
                } else {
                    model.launch { openOptions(appointment) }
                }
            },
        )
        LumeButton(
            content = LumeButtonContent.Title(stringResource(Res.string.detail_change)),
            size = LumeButtonSize.Bar,
            width = LumeButtonWidth.Fill,
            onClick = {
                model.launch { if (options.canChange) changeTime(appointment) else openOptions(appointment) }
            },
        )
    }
}

/** The clock time [minutes] later, on the same day: when an appointment ends. */
private fun ClockTime.plusMinutes(minutes: Int): ClockTime {
    val total = minuteOfDay + minutes
    return ClockTime(total / MINUTES_PER_HOUR, total % MINUTES_PER_HOUR)
}

private const val MINUTES_PER_HOUR = 60
