package com.luismejias.lumemedlink.core.device

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

internal actual fun platformPhotoCodec(): PhotoCodec = AndroidPhotoCodec

private object AndroidPhotoCodec : PhotoCodec {
    override fun encode(image: ImageBitmap): ByteArray? = ByteArrayOutputStream().use { out ->
        val written = image.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, out)
        out.toByteArray().takeIf { written && it.isNotEmpty() }
    }

    override fun decode(jpeg: ByteArray): ImageBitmap? =
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.asImageBitmap()
}
