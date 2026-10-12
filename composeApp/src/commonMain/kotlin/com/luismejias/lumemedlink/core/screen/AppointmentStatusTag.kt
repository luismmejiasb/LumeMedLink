package com.luismejias.lumemedlink.core.screen

import androidx.compose.runtime.Composable
import cl.lume.uicomposer.components.LumeTag
import cl.lume.uicomposer.components.LumeTagStyle
import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.status_cancelled
import com.luismejias.lumemedlink.resources.status_completed
import com.luismejias.lumemedlink.resources.status_confirmed
import com.luismejias.lumemedlink.resources.status_requested
import org.jetbrains.compose.resources.stringResource

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
