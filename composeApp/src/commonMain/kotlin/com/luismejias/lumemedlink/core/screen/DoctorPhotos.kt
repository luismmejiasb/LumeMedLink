package com.luismejias.lumemedlink.core.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.PhotoRead
import com.luismejias.lumemedlink.core.care.ProfessionalPhotoSource
import com.luismejias.lumemedlink.core.device.PhotoCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** How many professionals' photos are fetched at once: the platform has no batch read (its answer `0083`). */
internal const val MAX_PHOTO_READS = 4

/**
 * The professionals' photos the session has asked for, decoded, in memory only (ADR-0064 §4): the platform answers
 * `no-store`, and this model lives in the session's store, so a sign-out drops every one (§8.13). Each id is read once
 * per session; one that is absent or failed stays as the initials, in silence (ADR-0067).
 */
internal class DoctorPhotos(private val source: ProfessionalPhotoSource, private val codec: PhotoCodec) : ViewModel() {
    private val photos = mutableMapOf<String, MutableStateFlow<ImageBitmap?>>()
    private val reads = Semaphore(MAX_PHOTO_READS)

    /** The photo of [professionalId], or null — while it loads, when there is none, and when it failed. */
    fun photoOf(professionalId: String): StateFlow<ImageBitmap?> = photos.getOrPut(professionalId) {
        MutableStateFlow<ImageBitmap?>(null).also { held ->
            viewModelScope.launch {
                val read = reads.withPermit { source.professionalPhoto(professionalId) }
                held.value = (read as? PhotoRead.Present)?.jpeg?.let(codec::decode)
            }
        }
    }
}

/** The session's photos of professionals; null where none is provided (previews, the doctor's side): the initials. */
internal val LocalDoctorPhotos = staticCompositionLocalOf<DoctorPhotos?> { null }

/** [professionalId]'s photo as this composition sees it, or null for the initials. */
@Composable
internal fun doctorPhoto(professionalId: String): ImageBitmap? {
    if (professionalId.isBlank()) return null
    val photos = LocalDoctorPhotos.current ?: return null
    return photos.photoOf(professionalId).collectAsState().value
}
