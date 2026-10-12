package com.luismejias.lumemedlink.core.device

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.asImageBitmap

/**
 * What the Android shell hands this module for the profile photo (ADR-0050): the system photo picker (no permission,
 * and the app never sees the rest of the library) and the camera's preview, which returns a bitmap without a file. The
 * shell owns them because registering an activity result belongs to the activity; this module only asks.
 */
public interface AndroidPhotoHost {
    public val hasCamera: Boolean

    public fun pickFromGallery(onPicked: (Bitmap?) -> Unit)

    public fun takePhoto(onTaken: (Bitmap?) -> Unit)

    /** The system's settings for this app. */
    public fun openAppSettings()
}

/** Provided by the shell's activity around the app; absent (null) in a preview or a test, where nothing is offered. */
public val LocalAndroidPhotoHost: ProvidableCompositionLocal<AndroidPhotoHost?> = staticCompositionLocalOf { null }

@Composable
internal actual fun rememberPhotoPicker(onResult: (PhotoResult) -> Unit): PhotoPicker {
    val host = LocalAndroidPhotoHost.current
    val latest = rememberUpdatedState(onResult)
    return remember(host) {
        object : PhotoPicker {
            override val cameraAvailable: Boolean = host?.hasCamera == true

            override fun pick(source: PhotoSource) {
                val deliver: (Bitmap?) -> Unit = { bitmap ->
                    latest.value(bitmap?.let { PhotoResult.Picked(it.asImageBitmap()) } ?: PhotoResult.Cancelled)
                }
                when (source) {
                    PhotoSource.GALLERY -> host?.pickFromGallery(deliver)
                    PhotoSource.CAMERA -> host?.takePhoto(deliver)
                }
            }

            override fun openSettings() {
                host?.openAppSettings()
            }
        }
    }
}

/**
 * Reads a picked image into memory, scaled down first — a profile photo needs no more than a few hundred pixels — and
 * never into a file. For the shell's gallery result.
 */
public fun Context.decodePickedPhoto(uri: Uri): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
    return contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

private const val MAX_SIDE = 1024
