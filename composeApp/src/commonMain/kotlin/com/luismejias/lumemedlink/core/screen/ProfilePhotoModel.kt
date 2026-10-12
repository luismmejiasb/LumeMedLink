package com.luismejias.lumemedlink.core.screen

import androidx.compose.ui.graphics.ImageBitmap
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

    /** k2's «Usar foto»: the circle's crop replaces the initials everywhere (k3). */
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
