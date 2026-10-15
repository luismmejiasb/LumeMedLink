package com.luismejias.lumemedlink.core.care

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The signed-in person's profile photo on the platform (ADR-0064, backend request `0015`): one per account, JPEG, about
 * 512 px, cropped to the circle by the app. It travels through the stack and nowhere else — never a signed URL to a
 * storage host (ADR-0064 §2). The bytes are the platform's; the app never stores them (ADR-0050 §2, ADR-0064 §4).
 *
 * Who may read it is the platform's to enforce. A refusal (403) or nothing there (404) is [PhotoRead.Absent]: the app
 * shows the initials, never an error.
 */
internal interface ProfilePhotoGateway {
    suspend fun read(): PhotoRead

    /** Sends the cropped photo as JPEG. The server re-encodes it; the app writes no metadata. */
    suspend fun upload(jpeg: ByteArray): PhotoWrite

    suspend fun remove(): PhotoWrite
}

internal sealed interface PhotoRead {
    /** The photo the platform holds. Its bytes never reach a log, an event or analytics — not even their size. */
    class Present(val jpeg: ByteArray) : PhotoRead {
        override fun toString(): String = "Present(redacted)"
    }

    /** No photo, or one this person may not read (403, 404): the initials. */
    data object Absent : PhotoRead

    /** The read failed. The initials too, in silence: the photo is never a screen's own operation (ADR-0067). */
    data object Failed : PhotoRead
}

internal enum class PhotoWrite { DONE, FAILED }

/**
 * The stand-in until the contract has the photo (task `0150` of the backend, PENDING on 2026-10-15): the platform's
 * side kept in memory, so the flow runs end to end. Built inside the session's scope, so a sign-out drops it with
 * everything else (§8.13). Replaced, not wrapped, by the remote gateway when the contract lands.
 */
internal class InMemoryProfilePhoto : ProfilePhotoGateway {
    private var held: ByteArray? = null

    override suspend fun read(): PhotoRead = held?.let { PhotoRead.Present(it) } ?: PhotoRead.Absent

    override suspend fun upload(jpeg: ByteArray): PhotoWrite {
        held = jpeg
        return PhotoWrite.DONE
    }

    override suspend fun remove(): PhotoWrite {
        held = null
        return PhotoWrite.DONE
    }
}

/**
 * The photo as the session holds it, and the three things that change it (k1–k3). On bytes, so it is the same on both
 * platforms and testable without a canvas; the screen's model turns them into a bitmap.
 *
 * - **Reading** is silent: absent, refused or failed, the initials stay.
 * - **Uploading** keeps the previous photo until the platform has the new one (spec k3); a failure keeps the
 *   attempt for «Reintentar».
 * - **Removing** is the person's own act, so its failure is said.
 */
internal class ProfilePhotoSync(private val gateway: ProfilePhotoGateway) {
    private val held = MutableStateFlow<ByteArray?>(null)

    /** The photo to show, or null for the initials. */
    val photo: StateFlow<ByteArray?> = held.asStateFlow()

    private val failedUpload = MutableStateFlow(false)

    /** k3's «No pudimos subir tu foto · Reintentar». */
    val uploadFailed: StateFlow<Boolean> = failedUpload.asStateFlow()

    private var pending: ByteArray? = null

    suspend fun load() {
        held.value = (gateway.read() as? PhotoRead.Present)?.jpeg
    }

    /** True when the platform took it, and it replaces the initials. */
    suspend fun upload(jpeg: ByteArray): Boolean {
        pending = jpeg
        return when (gateway.upload(jpeg)) {
            PhotoWrite.DONE -> {
                held.value = jpeg
                pending = null
                failedUpload.value = false
                true
            }
            PhotoWrite.FAILED -> {
                failedUpload.value = true
                false
            }
        }
    }

    /** «Reintentar»: the same photo again. */
    suspend fun retry(): Boolean = pending?.let { upload(it) } ?: false

    /** «Ahora no»: the attempt is dropped, and the previous photo stays. */
    fun dropFailedUpload() {
        pending = null
        failedUpload.value = false
    }

    /** True when the platform no longer holds a photo, and the initials come back. */
    suspend fun remove(): Boolean = when (gateway.remove()) {
        PhotoWrite.DONE -> {
            held.value = null
            true
        }
        PhotoWrite.FAILED -> false
    }

    /** The logout's half in memory (§8.13): nothing of the photo outlives the session. */
    fun clear() {
        held.value = null
        pending = null
        failedUpload.value = false
    }
}
