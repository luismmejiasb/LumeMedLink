package com.luismejias.lumemedlink.core.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import cl.lume.uicomposer.components.LumeAlert
import cl.lume.uicomposer.components.LumeAlertAction
import cl.lume.uicomposer.components.LumeAlertPresentation
import cl.lume.uicomposer.components.LumeAvatar
import cl.lume.uicomposer.components.LumeAvatarAction
import cl.lume.uicomposer.components.LumeAvatarSize
import cl.lume.uicomposer.components.LumeImageCropper
import cl.lume.uicomposer.components.LumeImageCropperLabels
import cl.lume.uicomposer.components.LumeList
import cl.lume.uicomposer.components.LumeListAccessory
import cl.lume.uicomposer.components.LumeListChrome
import cl.lume.uicomposer.components.LumeListIconInk
import cl.lume.uicomposer.components.LumeListRow
import cl.lume.uicomposer.components.LumeListRowRole
import cl.lume.uicomposer.components.LumeSheet
import cl.lume.uicomposer.components.LumeToastHostState
import cl.lume.uicomposer.foundations.LumeIcon
import cl.lume.uicomposer.tokens.LumeStatusStyle
import cl.lume.uicomposer.tokens.LumeTileTint
import com.luismejias.lumemedlink.core.device.PhotoPicker
import com.luismejias.lumemedlink.core.device.PhotoSource
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.photo_cancel
import com.luismejias.lumemedlink.resources.photo_change
import com.luismejias.lumemedlink.resources.photo_crop_center
import com.luismejias.lumemedlink.resources.photo_crop_frame
import com.luismejias.lumemedlink.resources.photo_crop_help
import com.luismejias.lumemedlink.resources.photo_crop_title
import com.luismejias.lumemedlink.resources.photo_crop_use
import com.luismejias.lumemedlink.resources.photo_crop_zoom_in
import com.luismejias.lumemedlink.resources.photo_crop_zoom_out
import com.luismejias.lumemedlink.resources.photo_denied_camera_body
import com.luismejias.lumemedlink.resources.photo_denied_camera_title
import com.luismejias.lumemedlink.resources.photo_denied_gallery_body
import com.luismejias.lumemedlink.resources.photo_denied_gallery_title
import com.luismejias.lumemedlink.resources.photo_not_now
import com.luismejias.lumemedlink.resources.photo_open_settings
import com.luismejias.lumemedlink.resources.photo_options_title
import com.luismejias.lumemedlink.resources.photo_pick
import com.luismejias.lumemedlink.resources.photo_remove
import com.luismejias.lumemedlink.resources.photo_take
import com.luismejias.lumemedlink.resources.photo_updated
import org.jetbrains.compose.resources.stringResource

/**
 * The profile's avatar with the camera button (p7, m3): the photo once there is one (k3), the initials until then.
 * Tapping either opens k1.
 */
@Composable
internal fun ProfileAvatar(model: ProfilePhotoModel, initials: String, name: String, tint: LumeTileTint? = null) {
    val photo by model.photo.collectAsState()
    val action = LumeAvatarAction(LumeIcon.Camera, stringResource(Res.string.photo_change), model::openOptions)
    val shown = photo
    if (shown != null) {
        LumeAvatar(BitmapPainter(shown), size = LumeAvatarSize.Large, contentDescription = name, action = action)
    } else {
        LumeAvatar(initials, size = LumeAvatarSize.Large, tint = tint, contentDescription = name, action = action)
    }
}

private enum class PhotoOption { TAKE, PICK, REMOVE }

/**
 * k1–k4 over whichever screen shows the avatar: the options sheet, the circle (k2, over the whole screen, tab bar
 * included — so the host draws this last), «Foto actualizada» (k3) and the refusal with its way to the settings (k4).
 * The photo stays in memory (ADR-0050).
 */
@Composable
internal fun ProfilePhotoFlow(model: ProfilePhotoModel, picker: PhotoPicker, toasts: LumeToastHostState) {
    val step by model.step.collectAsState()
    val photo by model.photo.collectAsState()
    val updated by model.updated.collectAsState()
    val updatedText = stringResource(Res.string.photo_updated)
    LaunchedEffect(updated) {
        if (!updated) return@LaunchedEffect
        toasts.show(updatedText, LumeStatusStyle.Success)
        model.updatedSeen()
    }
    PhotoOptionsSheet(
        visible = step == PhotoStep.Options,
        // «Eliminar foto» only when there is one (spec k1); «Tomar foto» only on a phone with a camera.
        options = PhotoOption.entries.filter {
            when (it) {
                PhotoOption.TAKE -> picker.cameraAvailable
                PhotoOption.PICK -> true
                PhotoOption.REMOVE -> photo != null
            }
        },
        onChoose = { option ->
            when (option) {
                PhotoOption.TAKE -> {
                    model.dismiss()
                    picker.pick(PhotoSource.CAMERA)
                }
                PhotoOption.PICK -> {
                    model.dismiss()
                    picker.pick(PhotoSource.GALLERY)
                }
                PhotoOption.REMOVE -> model.remove()
            }
        },
        onDismiss = model::dismiss,
    )
    val denied = step as? PhotoStep.Denied
    val camera = denied?.source == PhotoSource.CAMERA
    LumeAlert(
        visible = denied != null,
        title = stringResource(
            if (camera) Res.string.photo_denied_camera_title else Res.string.photo_denied_gallery_title,
        ),
        message = stringResource(
            if (camera) Res.string.photo_denied_camera_body else Res.string.photo_denied_gallery_body,
        ),
        confirm = LumeAlertAction(stringResource(Res.string.photo_open_settings)) {
            model.dismiss()
            picker.openSettings()
        },
        cancel = LumeAlertAction(stringResource(Res.string.photo_not_now), onSelect = model::dismiss),
        style = LumeStatusStyle.Warning,
        presentation = LumeAlertPresentation.Result,
        glyph = if (camera) LumeIcon.Camera else LumeIcon.Photo,
    )
    (step as? PhotoStep.Cropping)?.let { cropping ->
        LumeImageCropper(
            image = cropping.image,
            labels = LumeImageCropperLabels(
                title = stringResource(Res.string.photo_crop_title),
                help = stringResource(Res.string.photo_crop_help),
                cancel = stringResource(Res.string.photo_cancel),
                use = stringResource(Res.string.photo_crop_use),
                frame = stringResource(Res.string.photo_crop_frame),
                zoomIn = stringResource(Res.string.photo_crop_zoom_in),
                zoomOut = stringResource(Res.string.photo_crop_zoom_out),
                center = stringResource(Res.string.photo_crop_center),
            ),
            onCancel = model::dismiss,
            onUse = model::framed,
        )
    }
}

/** k1: «Tomar foto», «Elegir de la galería» and, with a photo, «Eliminar foto»; «Cancelar» closes it. */
@Composable
private fun PhotoOptionsSheet(
    visible: Boolean,
    options: List<PhotoOption>,
    onChoose: (PhotoOption) -> Unit,
    onDismiss: () -> Unit,
) {
    LumeSheet(
        visible = visible,
        onDismiss = onDismiss,
        title = stringResource(Res.string.photo_options_title),
        dismissLabel = stringResource(Res.string.photo_cancel),
    ) {
        LumeList(
            items = options,
            key = { it },
            scrolls = false,
            background = Color.Transparent,
            chrome = LumeListChrome.OpenRuled,
            onRowTap = onChoose,
        ) { option ->
            when (option) {
                PhotoOption.TAKE -> LumeListRow(
                    title = stringResource(Res.string.photo_take),
                    leading = LumeListAccessory.Icon(LumeIcon.Camera, "", ink = LumeListIconInk.Accent),
                )
                PhotoOption.PICK -> LumeListRow(
                    title = stringResource(Res.string.photo_pick),
                    leading = LumeListAccessory.Icon(LumeIcon.Photo, "", ink = LumeListIconInk.Accent),
                )
                PhotoOption.REMOVE -> LumeListRow(
                    title = stringResource(Res.string.photo_remove),
                    leading = LumeListAccessory.Icon(LumeIcon.Trash, ""),
                    role = LumeListRowRole.Destructive,
                )
            }
        }
    }
}
