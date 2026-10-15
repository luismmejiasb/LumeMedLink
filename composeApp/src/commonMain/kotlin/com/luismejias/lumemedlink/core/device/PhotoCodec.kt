package com.luismejias.lumemedlink.core.device

import androidx.compose.ui.graphics.ImageBitmap

/**
 * The photo between the screen and the platform (ADR-0064): the cropped bitmap out as JPEG, and the platform's bytes
 * back as a bitmap the view draws — never a URL. In memory both ways; nothing touches a file.
 */
internal interface PhotoCodec {
    /** Null when the platform could not encode it: the upload fails as any other would. */
    fun encode(image: ImageBitmap): ByteArray?

    /** Null when the bytes are not an image this platform can draw: the initials then. */
    fun decode(jpeg: ByteArray): ImageBitmap?
}

/** The platform's JPEG codec: Android's `Bitmap.compress` and `BitmapFactory`, Skia on iOS. */
internal expect fun platformPhotoCodec(): PhotoCodec

/** The quality the app encodes at. The server re-encodes anyway (request `0015` §3). */
internal const val PHOTO_JPEG_QUALITY = 85
