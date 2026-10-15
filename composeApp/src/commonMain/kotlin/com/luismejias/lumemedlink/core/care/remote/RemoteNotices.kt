package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.NoticeMark
import com.luismejias.lumemedlink.core.care.NoticesSource
import com.luismejias.lumemedlink.core.care.PatientNotice
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.contract.ContractOperations
import kotlinx.datetime.TimeZone

/**
 * The patient's inbox over the contract (task `0037`): `listMyNotices`, newest first. The platform keeps no read mark,
 * so what is new is what was sent after the newest notice shown, kept in [mark]. Each notice names its clinic by the
 * patient's own profile there (`listMyPatientProfiles`), as the contract says to.
 *
 * Only the first page is read: it is the newest, and the bell asks only whether the newest is new.
 */
internal class RemoteNotices(
    private val contract: ContractClient,
    private val mark: NoticeMark,
    /** A notice names no place, so it is shown on the clinics' own wall clock, the fallback zone (ADR-0063). */
    private val zone: TimeZone = PlaceZones().fallback,
) : NoticesSource {
    override suspend fun hasUnreadNotices(): CareResult<Boolean> = careRead {
        val newest = contract.call(ContractOperations.LIST_MY_NOTICES).items.maxOfOrNull { epochMillisOf(it.createdAt) }
        val seen = mark.seenUntil()
        newest != null && (seen == null || newest > seen)
    }

    override suspend fun notices(): CareResult<List<PatientNotice>> = careRead {
        val clinics = contract.call(ContractOperations.LIST_MY_PATIENT_PROFILES).associate {
            it.tenantId to
                it.tenantName
        }
        contract.call(ContractOperations.LIST_MY_NOTICES).items
            .map {
                val wall = wallTimeOf(it.createdAt, zone)
                PatientNotice(
                    id = it.id,
                    clinic = clinics[it.clinicId].orEmpty(),
                    title = it.title,
                    text = it.text,
                    sentAtEpochMillis = epochMillisOf(it.createdAt),
                    sentOn = wall.date,
                    sentAt = wall.time,
                )
            }
            .sortedByDescending { it.sentAtEpochMillis }
    }

    override suspend fun noticesSeen(newestEpochMillis: Long) = mark.seen(newestEpochMillis)
}
