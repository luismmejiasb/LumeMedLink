package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.PhotoRead
import com.luismejias.lumemedlink.core.care.PhotoWrite
import com.luismejias.lumemedlink.core.care.ProfilePhotoGateway
import com.luismejias.lumemedlink.core.networking.AppErrorException
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.contract.ContractImage
import com.luismejias.lumemedlink.core.networking.contract.ContractOperations
import com.luismejias.lumemedlink.shared.AppError
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** The media type the cropped photo is sent as: the app encodes the crop as JPEG, upright (ADR-0064). */
private const val JPEG = "image/jpeg"

/**
 * The patient's own photo over the contract (ADR-0064, the platform's `ADR-0106`, its answer `0082`): `getMyPhoto`,
 * `putMyPhoto` and `deleteMyPhoto`, raw bytes through the stack. Replaces the in-memory stand-in wherever the platform
 * answers. The platform re-encodes what it keeps, so the app sends the crop and nothing about it.
 */
internal class RemoteProfilePhoto(private val contract: ContractClient) : ProfilePhotoGateway {
    override suspend fun read(): PhotoRead = try {
        PhotoRead.Present(contract.call(ContractOperations.GET_MY_PHOTO).bytes)
    } catch (refused: AppErrorException) {
        currentCoroutineContext().ensureActive()
        // 404 is «no photo»; a 403 the initials as well, in silence (ADR-0064).
        if (refused.error.isAbsence()) PhotoRead.Absent else PhotoRead.Failed
    }

    override suspend fun upload(jpeg: ByteArray): PhotoWrite = write {
        contract.call(ContractOperations.PUT_MY_PHOTO, ContractImage(jpeg, JPEG))
    }

    /** Removing a photo that is not there leaves what the person asked for: none. */
    override suspend fun remove(): PhotoWrite = try {
        contract.call(ContractOperations.DELETE_MY_PHOTO)
        PhotoWrite.DONE
    } catch (refused: AppErrorException) {
        currentCoroutineContext().ensureActive()
        if (refused.error is AppError.NotFound) PhotoWrite.DONE else PhotoWrite.FAILED
    }

    private suspend fun write(change: suspend () -> Unit): PhotoWrite = try {
        change()
        PhotoWrite.DONE
    } catch (@Suppress("SwallowedException") refused: AppErrorException) {
        currentCoroutineContext().ensureActive()
        // A refused photo (415, 422, 413) leaves the previous one where it was: k3 offers «Reintentar» either way.
        PhotoWrite.FAILED
    }
}

/** A photo that is not there, or not this person's to see: both read as the initials. */
internal fun AppError.isAbsence(): Boolean =
    this is AppError.NotFound || (this is AppError.Unexpected && status == HTTP_FORBIDDEN)

private const val HTTP_FORBIDDEN = 403
