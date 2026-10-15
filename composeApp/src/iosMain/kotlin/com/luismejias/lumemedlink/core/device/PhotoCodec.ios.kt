package com.luismejias.lumemedlink.core.device

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

internal actual fun platformPhotoCodec(): PhotoCodec = SkiaPhotoCodec

private object SkiaPhotoCodec : PhotoCodec {
    override fun encode(image: ImageBitmap): ByteArray? =
        Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.JPEG, PHOTO_JPEG_QUALITY)?.bytes

    // A malformed answer is no photo, not a crash: Skia throws on bytes it cannot decode.
    @Suppress("SwallowedException")
    override fun decode(jpeg: ByteArray): ImageBitmap? = try {
        Image.makeFromEncoded(jpeg).toComposeImageBitmap()
    } catch (unreadable: IllegalArgumentException) {
        null
    }
}
