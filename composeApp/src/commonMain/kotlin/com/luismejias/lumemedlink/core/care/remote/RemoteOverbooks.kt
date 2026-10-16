package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.CareDoctor
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.DirectorySearch
import com.luismejias.lumemedlink.core.care.OverbookDraft
import com.luismejias.lumemedlink.core.care.OverbookFailure
import com.luismejias.lumemedlink.core.care.OverbookReason
import com.luismejias.lumemedlink.core.care.OverbookRequest
import com.luismejias.lumemedlink.core.care.OverbookRequestStatus
import com.luismejias.lumemedlink.core.care.OverbookRequests
import com.luismejias.lumemedlink.core.care.OverbookResult
import com.luismejias.lumemedlink.core.care.OverbookUrgency
import com.luismejias.lumemedlink.core.care.secondary
import com.luismejias.lumemedlink.core.networking.AppErrorException
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.contract.ContractOperations
import com.luismejias.lumemedlink.core.networking.contract.MyOverbookRequestDto
import com.luismejias.lumemedlink.core.networking.contract.RequestMyOverbookDto
import com.luismejias.lumemedlink.core.networking.contract.RequestMyOverbookDtoReason
import com.luismejias.lumemedlink.core.networking.contract.RequestMyOverbookDtoUrgency
import com.luismejias.lumemedlink.shared.AppError
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.TimeZone

/**
 * Overbook requests over the contract (ADR-0069, the platform's `ADR-0105`): `requestMyOverbook`,
 * `listMyOverbookRequests` and `withdrawMyOverbookRequest`. The list names each professional by the directory and each
 * clinic by the patient's own profile there; both only label it (ADR-0067), so either may be missing. The reason and
 * the urgency the platform returns are dropped here: nothing past this class can show them (ADR-0069 point 2).
 */
internal class RemoteOverbooks(
    private val contract: ContractClient,
    private val directory: DirectorySearch,
    /** A request names no place, so its day is the clinics' own wall calendar, the fallback zone (ADR-0063). */
    private val zone: TimeZone = PlaceZones().fallback,
) : OverbookRequests {
    override suspend fun overbookRequests(): CareResult<List<OverbookRequest>> = careRead {
        val items = contract.call(ContractOperations.LIST_MY_OVERBOOK_REQUESTS).items
        val clinics = secondary(emptyMap()) {
            careRead {
                contract.call(ContractOperations.LIST_MY_PATIENT_PROFILES).associate { it.tenantId to it.tenantName }
            }
        }
        val doctors = items.map { it.professionalProfileId }.distinct().associateWith { id ->
            secondary(null) { directory.doctor(id) }?.doctor
        }
        items.map {
            request(
                it.id,
                doctors[it.professionalProfileId],
                clinics[it.clinicId].orEmpty(),
                it.status,
                it.origin,
                it.createdAt,
            )
        }
    }

    override suspend fun requestOverbook(draft: OverbookDraft, clientId: String): OverbookResult = overbookChange {
        contract.call(
            ContractOperations.REQUEST_MY_OVERBOOK,
            RequestMyOverbookDto(
                professionalId = draft.doctorId,
                urgency = draft.urgency.wire,
                reason = draft.reason.wire,
                clientId = clientId,
            ),
        )
    }

    override suspend fun withdrawOverbookRequest(request: OverbookRequest): OverbookResult = overbookChange {
        contract.call(ContractOperations.WITHDRAW_MY_OVERBOOK_REQUEST, path = mapOf("id" to request.id))
    }

    /** A write's answer, named without a second read: the doctor and the clinic are the list's to name. */
    private suspend fun overbookChange(change: suspend () -> MyOverbookRequestDto): OverbookResult = try {
        val written = change()
        OverbookResult.Done(
            request(written.id, null, "", written.status, written.origin, written.createdAt),
        )
    } catch (refused: AppErrorException) {
        currentCoroutineContext().ensureActive()
        OverbookResult.Failed(refused.error.overbookFailure())
    }

    @Suppress("LongParameterList")
    private fun request(
        id: String,
        doctor: CareDoctor?,
        clinic: String,
        status: String,
        origin: String,
        createdAt: String,
    ) = OverbookRequest(
        id = id,
        doctor = doctor,
        clinic = clinic,
        status = overbookStatusOf(status),
        askedOn = wallTimeOf(createdAt, zone).date,
        byPhone = origin == "PHONE",
    )
}

/** The contract's open vocabulary: a status this app does not know is shown without a verdict, never as an error. */
internal fun overbookStatusOf(wire: String): OverbookRequestStatus =
    OverbookRequestStatus.entries.firstOrNull { it != OverbookRequestStatus.OTHER && it.name == wire }
        ?: OverbookRequestStatus.OTHER

/** The platform's refusals, as the sheet and the list name them. */
internal fun AppError.overbookFailure(): OverbookFailure = when {
    this is AppError.NotFound -> OverbookFailure.NotTaken
    problemCode() == "pending_request_exists" -> OverbookFailure.AlreadyWaiting
    problemCode() == "conflict" -> OverbookFailure.NoLongerWaiting
    this is AppError.Retryable && offline -> OverbookFailure.Offline
    else -> OverbookFailure.Unavailable
}

private val OverbookUrgency.wire: RequestMyOverbookDtoUrgency get() = RequestMyOverbookDtoUrgency(name)

/** The contract's closed list, by the same words (ADR-0069). */
private val OverbookReason.wire: RequestMyOverbookDtoReason get() = RequestMyOverbookDtoReason(name)
