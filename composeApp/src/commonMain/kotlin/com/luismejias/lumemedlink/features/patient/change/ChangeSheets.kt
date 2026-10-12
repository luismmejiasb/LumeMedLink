package com.luismejias.lumemedlink.features.patient.change

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cl.lume.uicomposer.components.LumeAvatarSize
import cl.lume.uicomposer.components.LumeCard
import cl.lume.uicomposer.components.LumeCardStyle
import cl.lume.uicomposer.components.LumeFieldState
import cl.lume.uicomposer.components.LumeList
import cl.lume.uicomposer.components.LumeListAccessory
import cl.lume.uicomposer.components.LumeListChrome
import cl.lume.uicomposer.components.LumeListIconInk
import cl.lume.uicomposer.components.LumeListRow
import cl.lume.uicomposer.components.LumeListRowRole
import cl.lume.uicomposer.components.LumeListRowTextStyle
import cl.lume.uicomposer.components.LumeListTileEmphasis
import cl.lume.uicomposer.components.LumeSheet
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTextStyle
import cl.lume.uicomposer.foundations.LumeIcon
import cl.lume.uicomposer.tokens.LumeTileTint
import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.device.AddressClipboard
import com.luismejias.lumemedlink.core.device.Directions
import com.luismejias.lumemedlink.core.device.MapApp
import com.luismejias.lumemedlink.features.patient.parts.DoctorIdentity
import com.luismejias.lumemedlink.features.patient.parts.capitalized
import com.luismejias.lumemedlink.features.patient.parts.relativeDayText
import com.luismejias.lumemedlink.features.patient.parts.richRuns
import com.luismejias.lumemedlink.features.patient.parts.shortDateText
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.directions_close
import com.luismejias.lumemedlink.resources.directions_copy
import com.luismejias.lumemedlink.resources.directions_title
import com.luismejias.lumemedlink.resources.options_call
import com.luismejias.lumemedlink.resources.options_cancel
import com.luismejias.lumemedlink.resources.options_cancel_body
import com.luismejias.lumemedlink.resources.options_change
import com.luismejias.lumemedlink.resources.options_change_body
import com.luismejias.lumemedlink.resources.options_close
import com.luismejias.lumemedlink.resources.options_title
import com.luismejias.lumemedlink.resources.options_window
import com.luismejias.lumemedlink.resources.options_window_closed
import com.luismejias.lumemedlink.resources.options_withdraw_body
import com.luismejias.lumemedlink.resources.window_hours
import com.luismejias.lumemedlink.resources.window_minutes
import com.luismejias.lumemedlink.shared.CalendarDate
import org.jetbrains.compose.resources.stringResource

/** "Hoy · 10:30", "Lun 21 sep · 09:00": when an appointment is, as a line under the doctor's name. */
@Composable
internal fun whenText(appointment: PatientAppointment, today: CalendarDate): String =
    "${relativeDayText(appointment.date, today) ?: shortDateText(appointment.date).capitalized()} · " +
        appointment.start.display

/** "30 min", "24 h": a doctor's change window as f2 writes it. */
@Composable
internal fun windowText(minutes: Int): String = if (minutes % MINUTES_PER_HOUR == 0) {
    stringResource(Res.string.window_hours, minutes / MINUTES_PER_HOUR)
} else {
    stringResource(Res.string.window_minutes, minutes)
}

private const val MINUTES_PER_HOUR = 60

/**
 * f1 «Cómo llegar»: the map apps installed, and copying the address (ADR-0048, ADR-0049). With one app only, it opens
 * directly and no sheet is drawn (spec f1) — the caller decides that before showing this.
 */
@Composable
internal fun DirectionsSheet(
    appointment: PatientAppointment?,
    apps: List<MapApp>,
    directions: Directions,
    clipboard: AddressClipboard,
    onCopied: () -> Unit,
    onDismiss: () -> Unit,
) {
    val address = appointment?.let { "${it.place.address}\n${it.place.detail}" }.orEmpty()
    LumeSheet(
        visible = appointment != null,
        onDismiss = onDismiss,
        title = stringResource(Res.string.directions_title),
        dismissLabel = stringResource(Res.string.directions_close),
        subtitle = address,
    ) {
        if (apps.isNotEmpty()) {
            LumeList(
                items = apps,
                key = { it.id },
                scrolls = false,
                background = Color.Transparent,
                chrome = LumeListChrome.Open,
                onRowTap = { app ->
                    onDismiss()
                    directions.open(app, address.replace('\n', ' '))
                },
            ) { app ->
                LumeListRow(
                    title = app.name,
                    leading = LumeListAccessory.AppImage(app.icon ?: LumeIcon.Directions.painter()),
                    disclosure = true,
                )
            }
        }
        val copyLabel = stringResource(Res.string.directions_copy)
        // Its own open card, as f1 draws it: the list is the card, so no LumeCard around it.
        LumeList(
            items = listOf(Unit),
            key = { it },
            scrolls = false,
            background = Color.Transparent,
            chrome = LumeListChrome.Open,
            onRowTap = {
                clipboard.copy(address.replace('\n', ' '))
                onDismiss()
                onCopied()
            },
        ) {
            LumeListRow(
                title = copyLabel,
                leading = LumeListAccessory.Icon(LumeIcon.Copy, "", ink = LumeListIconInk.Accent),
                role = LumeListRowRole.Action,
            )
        }
    }
}

private enum class OptionRow { CHANGE, CANCEL }

/**
 * f2 «¿Qué quieres hacer?». Past the doctor's window both choices are drawn and refused, never hidden, and the
 * practice's phone is offered when the doctor shares it (spec f2).
 */
@Composable
internal fun OptionsSheet(
    options: ChangeOptions?,
    today: CalendarDate,
    onChange: (PatientAppointment) -> Unit,
    onCancel: (PatientAppointment) -> Unit,
    onCall: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    LumeSheet(
        visible = options != null,
        onDismiss = onDismiss,
        title = stringResource(Res.string.options_title),
        dismissLabel = stringResource(Res.string.options_close),
    ) {
        val shown = options ?: return@LumeSheet
        val appointment = shown.appointment
        LumeCard(modifier = Modifier.fillMaxWidth(), style = LumeCardStyle.Filled) {
            DoctorIdentity(
                appointment.doctor,
                listOf("${whenText(appointment, today)} · ${appointment.doctor.specialty}"),
                avatar = LumeAvatarSize.Small,
            )
        }
        val withdraws = appointment.status == AppointmentStatus.REQUESTED
        val rows = remember(shown) { OptionRow.entries }
        LumeList(
            items = rows,
            key = { it },
            scrolls = false,
            background = Color.Transparent,
            chrome = LumeListChrome.Open,
            tapsRow = { row -> if (row == OptionRow.CHANGE) shown.canChange else shown.canCancel },
            onRowTap = { row -> if (row == OptionRow.CHANGE) onChange(appointment) else onCancel(appointment) },
        ) { row ->
            when (row) {
                OptionRow.CHANGE -> LumeListRow(
                    title = stringResource(Res.string.options_change),
                    description = stringResource(Res.string.options_change_body),
                    leading = LumeListAccessory.Tile(
                        LumeIcon.Calendar,
                        LumeTileTint.Azure,
                        emphasis = LumeListTileEmphasis.Washed,
                    ),
                    textStyle = LumeListRowTextStyle.Strong,
                    disclosure = shown.canChange,
                    state = if (shown.canChange) LumeFieldState.Enabled else LumeFieldState.Disabled,
                )
                OptionRow.CANCEL -> LumeListRow(
                    title = stringResource(Res.string.options_cancel),
                    description = stringResource(
                        if (withdraws) Res.string.options_withdraw_body else Res.string.options_cancel_body,
                    ),
                    leading = LumeListAccessory.Tile(
                        LumeIcon.XCircleFill,
                        LumeTileTint.Rose,
                        emphasis = LumeListTileEmphasis.Washed,
                    ),
                    textStyle = LumeListRowTextStyle.Strong,
                    role = LumeListRowRole.Destructive,
                    disclosure = shown.canCancel,
                    state = if (shown.canCancel) LumeFieldState.Enabled else LumeFieldState.Disabled,
                )
            }
        }
        if (shown.canChange || withdraws && shown.canCancel) {
            LumeText(
                runs = stringResource(Res.string.options_window, windowText(shown.cutoffMinutes)).richRuns(),
                style = LumeTextStyle.TextSmall,
                color = LumeTextColor.Secondary,
            )
        } else {
            LumeText(
                text = stringResource(Res.string.options_window_closed),
                style = LumeTextStyle.TextSmall,
                color = LumeTextColor.Secondary,
            )
        }
        shown.practicePhone?.let { phone ->
            val call = stringResource(Res.string.options_call)
            LumeList(
                items = listOf(phone),
                key = { it },
                scrolls = false,
                background = Color.Transparent,
                chrome = LumeListChrome.Open,
                onRowTap = onCall,
            ) {
                LumeListRow(title = call, leading = LumeListAccessory.Icon(LumeIcon.Phone, ""), disclosure = true)
            }
        }
    }
}
