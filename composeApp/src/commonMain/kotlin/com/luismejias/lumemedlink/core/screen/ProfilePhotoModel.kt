package com.luismejias.lumemedlink.core.screen

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import com.luismejias.lumemedlink.core.device.PhotoResult
import com.luismejias.lumemedlink.core.device.PhotoSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the photo flow stands (k1–k4). */
internal sealed interface PhotoStep {
    /** k1: take, pick, or — when there is one — remove. */
    data object Options : PhotoStep

    /** k2: the picked photo under the circle. */
    class Cropping(val image: ImageBitmap) : PhotoStep {
        override fun toString(): String = "Cropping(redacted)"
    }

    /** k4: the system refused [source]. */
    data class Denied(val source: PhotoSource) : PhotoStep
}

/**
 * The profile photo of whoever is signed in — the patient's (p7) or the doctor's (m3) — and the flow that changes it.
 * In memory only (ADR-0050): it lives in the session's store, so a sign-out takes it, and nothing writes it anywhere.
 */
internal class ProfilePhotoModel : ViewModel() {
    private val current = MutableStateFlow<ImageBitmap?>(null)
    val photo: StateFlow<ImageBitmap?> = current.asStateFlow()

    private val shown = MutableStateFlow<PhotoStep?>(null)
    val step: StateFlow<PhotoStep?> = shown.asStateFlow()

    private val updatedFlag = MutableStateFlow(false)
    val updated: StateFlow<Boolean> = updatedFlag.asStateFlow()

    fun openOptions() {
        shown.value = PhotoStep.Options
    }

    fun dismiss() {
        shown.value = null
    }

    /** What the system's picker or camera answered. */
    fun picked(result: PhotoResult) {
        shown.value = when (result) {
            is PhotoResult.Picked -> PhotoStep.Cropping(result.image)
            PhotoResult.Cancelled -> null
            is PhotoResult.Denied -> PhotoStep.Denied(result.source)
        }
    }

    /** k2's «Usar foto» with the square the circle framed, in the picked photo's pixels (the kit's cropper). */
    fun framed(square: IntRect) {
        val picked = (shown.value as? PhotoStep.Cropping)?.image ?: return
        cropped(picked.croppedSquare(square))
    }

    /** The circle's crop replaces the initials everywhere (k3). */
    fun cropped(image: ImageBitmap) {
        current.value = image
        shown.value = null
        updatedFlag.value = true
    }

    /** k1's «Eliminar foto»: the initials come back. */
    fun remove() {
        current.value = null
        shown.value = null
    }

    fun updatedSeen() {
        updatedFlag.value = false
    }
}

/** The photo's longest side once cropped: an avatar never needs more, and the spec compresses on the phone (k2). */
internal const val PHOTO_SIDE = 512

/** [square] of this photo, scaled down to [PHOTO_SIDE] at most — a new bitmap in memory, never a file (ADR-0050). */
internal fun ImageBitmap.croppedSquare(square: IntRect): ImageBitmap {
    val side = minOf(square.width, square.height, PHOTO_SIDE).coerceAtLeast(1)
    val out = ImageBitmap(side, side)
    Canvas(out).drawImageRect(
        image = this,
        srcOffset = IntOffset(square.left, square.top),
        srcSize = IntSize(square.width, square.height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(side, side),
        paint = Paint().apply { filterQuality = FilterQuality.High },
    )
    return out
}
