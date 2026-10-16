package com.luismejias.lumemedlink.features.patient.parts

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.BitmapPainter
import cl.lume.uicomposer.components.LumeAvatar
import cl.lume.uicomposer.components.LumeAvatarSize
import cl.lume.uicomposer.components.LumeListAccessory
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeStackAxis
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTextStyle
import cl.lume.uicomposer.components.LumeTextWeight
import cl.lume.uicomposer.tokens.LumeTileTint
import com.luismejias.lumemedlink.core.care.CareDoctor
import com.luismejias.lumemedlink.core.screen.doctorPhoto

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
        DoctorAvatar(doctor, avatar)
        LumeStack(alignment = LumeStackAlignment.Start, spacing = { xSmall }) {
            LumeText(text = doctor.displayName, weight = LumeTextWeight.Strong)
            details.forEach { LumeText(text = it, style = LumeTextStyle.TextSmall, color = LumeTextColor.Secondary) }
            extra?.invoke()
        }
    }
}

/**
 * A doctor's face where a card heads with them: their photo when the platform lets this patient see it (backend
 * request `0016`), else their initials on their stable colour — while it loads, when there is none, when it failed.
 */
@Composable
internal fun DoctorAvatar(doctor: CareDoctor, size: LumeAvatarSize = LumeAvatarSize.Medium) {
    val photo = doctorPhoto(doctor.id)
    if (photo != null) {
        LumeAvatar(BitmapPainter(photo), contentDescription = doctor.displayName, size = size)
    } else {
        LumeAvatar(
            initials = doctor.initials,
            size = size,
            tint = LumeTileTint.deterministic(doctor.id),
            contentDescription = doctor.displayName,
        )
    }
}
