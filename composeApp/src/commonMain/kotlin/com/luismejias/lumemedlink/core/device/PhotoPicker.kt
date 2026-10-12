package com.luismejias.lumemedlink.core.device

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** Where a profile photo comes from (k1). */
internal enum class PhotoSource { CAMERA, GALLERY }

internal sealed interface PhotoResult {
    /** The photo, decoded in memory — never written to disk (ADR-0050). */
    class Picked(val image: ImageBitmap) : PhotoResult {
        override fun toString(): String = "Picked(redacted)"
    }

    data object Cancelled : PhotoResult

    /** The system refused [source]: k4 says so and offers the app's settings. */
    data class Denied(val source: PhotoSource) : PhotoResult
}

/**
 * The profile photo's two doors (k1, ADR-0050): the system's photo picker, which asks no permission on either
 * platform, and the camera, which iOS asks for the first time it is used. What comes back is a bitmap in memory;
 * nothing is saved, and nothing leaves the phone.
 */
internal interface PhotoPicker {
    /** Whether this device has a camera to offer «Tomar foto» at all. */
    val cameraAvailable: Boolean

    fun pick(source: PhotoSource)

    /** k4's «Abrir Ajustes»: the system's settings for this app, where a refused permission is given back. */
    fun openSettings()
}

@Composable
internal expect fun rememberPhotoPicker(onResult: (PhotoResult) -> Unit): PhotoPicker
