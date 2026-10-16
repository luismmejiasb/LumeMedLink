package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.PhotoRead
import com.luismejias.lumemedlink.core.care.ProfessionalPhotoSource
import com.luismejias.lumemedlink.core.networking.AppErrorException
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.contract.ContractOperations
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A professional's photo over the contract: `getProfessionalPhoto`, by the same opaque id the directory gives. */
internal class RemoteProfessionalPhoto(private val contract: ContractClient) : ProfessionalPhotoSource {
    override suspend fun professionalPhoto(professionalId: String): PhotoRead = try {
        val image = contract.call(
            ContractOperations.GET_PROFESSIONAL_PHOTO,
            path = mapOf("professionalId" to professionalId),
        )
        PhotoRead.Present(image.bytes)
    } catch (refused: AppErrorException) {
        currentCoroutineContext().ensureActive()
        if (refused.error.isAbsence()) PhotoRead.Absent else PhotoRead.Failed
    }
}
