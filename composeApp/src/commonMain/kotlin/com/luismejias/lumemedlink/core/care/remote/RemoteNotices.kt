package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.NoticeMark
import com.luismejias.lumemedlink.core.care.NoticesSource
import com.luismejias.lumemedlink.core.care.PatientNotice
import com.luismejias.lumemedlink.core.care.secondary
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.contract.ContractOperations
import kotlinx.datetime.TimeZone

/**
 * The patient's inbox over the contract (task `0037`): `listMyNotices`, newest first. The platform keeps no read mark,
 * so what is new is what was sent after the newest notice shown, kept in [mark]. Each notice names its clinic by the
 * patient's own profile there (`listMyPatientProfiles`), as the contract says to.
 *
 * Only the first page is read: it is the newest, and the bell counts what on it is new.
 */
internal class RemoteNotices(
    private val contract: ContractClient,
    private val mark: NoticeMark,
    /** A notice names no place, so it is shown on the clinics' own wall clock, the fallback zone (ADR-0063). */
    private val zone: TimeZone = PlaceZones().fallback,
) : NoticesSource {
    override suspend fun unreadNoticeCount(): CareResult<Int> = careRead {
        val sent = contract.call(ContractOperations.LIST_MY_NOTICES).items.map { epochMillisOf(it.createdAt) }
        val seen = mark.seenUntil()
        sent.count { seen == null || it > seen }
    }

    override suspend fun notices(): CareResult<List<PatientNotice>> = careRead {
        // The inbox is the screen's operation; the clinics' names only label it (ADR-0067): unnamed when unreadable.
        val clinics = secondary(emptyMap()) {
            careRead {
                contract.call(ContractOperations.LIST_MY_PATIENT_PROFILES).associate { it.tenantId to it.tenantName }
            }
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
