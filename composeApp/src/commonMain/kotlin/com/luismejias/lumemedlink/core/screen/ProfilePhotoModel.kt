package com.luismejias.lumemedlink.core.screen

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.ProfilePhotoGateway
import com.luismejias.lumemedlink.core.care.ProfilePhotoSync
import com.luismejias.lumemedlink.core.device.PhotoCodec
import com.luismejias.lumemedlink.core.device.PhotoResult
import com.luismejias.lumemedlink.core.device.PhotoSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    /** k1's «Eliminar foto», asked again: removing is explicit and confirmed. */
    data object ConfirmRemove : PhotoStep

    /** k3's «No pudimos subir tu foto · Reintentar»: the previous photo stays. */
    data object UploadFailed : PhotoStep
}

/** What the person is told once a change settles. */
internal enum class PhotoNews { UPDATED, REMOVE_FAILED }

/**
 * The profile photo of whoever is signed in — the patient's (p7) or the doctor's (m3) — and the flow that changes it.
 * The bytes go to the platform through [gateway] (ADR-0064) and come back as a bitmap the view draws, never a URL. The
 * bitmap lives in this model, in the session's store: a sign-out clears the store, and with it the photo (§8.13).
 * Nothing writes it anywhere (ADR-0050 §2's in-memory rule still holds on the phone).
 */
internal class ProfilePhotoModel(gateway: ProfilePhotoGateway, private val codec: PhotoCodec) : ViewModel() {
    private val sync = ProfilePhotoSync(gateway)

    /** The photo to draw, or null for the initials — while it loads, when there is none, and when it failed. */
    val photo: StateFlow<ImageBitmap?> = sync.photo
        .map { bytes -> bytes?.let(codec::decode) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val shown = MutableStateFlow<PhotoStep?>(null)
    val step: StateFlow<PhotoStep?> = shown.asStateFlow()

    private val told = MutableStateFlow<PhotoNews?>(null)
    val news: StateFlow<PhotoNews?> = told.asStateFlow()

    init {
        // A secondary read (ADR-0067): absent, refused or failed, the initials stay and nothing is said.
        viewModelScope.launch { sync.load() }
    }

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

    /** The circle's crop goes to the platform; it replaces the initials once the platform has it (k3). */
    fun cropped(image: ImageBitmap) {
        shown.value = null
        val jpeg = codec.encode(image)
        viewModelScope.launch {
            val done = jpeg != null && sync.upload(jpeg)
            if (done) told.value = PhotoNews.UPDATED else shown.value = PhotoStep.UploadFailed
        }
    }

    /** k3's «Reintentar». */
    fun retryUpload() {
        shown.value = null
        viewModelScope.launch {
            if (sync.retry()) told.value = PhotoNews.UPDATED else shown.value = PhotoStep.UploadFailed
        }
    }

    /** k3's «Ahora no»: the previous photo stays. */
    fun dropFailedUpload() {
        sync.dropFailedUpload()
        shown.value = null
    }

    /** k1's «Eliminar foto»: asked again before anything is removed. */
    fun askRemove() {
        shown.value = PhotoStep.ConfirmRemove
    }

    /** Confirmed: the platform drops the photo and the initials come back. */
    fun remove() {
        shown.value = null
        viewModelScope.launch { if (!sync.remove()) told.value = PhotoNews.REMOVE_FAILED }
    }

    fun newsSeen() {
        told.value = null
    }

    override fun onCleared() {
        sync.clear()
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
