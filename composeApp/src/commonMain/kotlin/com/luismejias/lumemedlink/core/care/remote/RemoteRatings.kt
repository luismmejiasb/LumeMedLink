package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.AppointmentsSource
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.RatingAspect
import com.luismejias.lumemedlink.core.care.RatingDraft
import com.luismejias.lumemedlink.core.care.RatingPrompt
import com.luismejias.lumemedlink.core.care.RatingsGateway
import com.luismejias.lumemedlink.core.care.SentRating
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.contract.ContractOperations
import com.luismejias.lumemedlink.core.networking.contract.RateAttentionDto
import com.luismejias.lumemedlink.core.networking.contract.RateAttentionDtoAspectsItem
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The version of the consent the rating sheet shows (`rate_consent`), kept by the platform with each rating as the
 * record of the person's consent (`rateMyAttention`). A change to that text is a new version here, in the same change.
 */
internal const val RATING_CONSENT_VERSION = "lumemedlink-rating-2026-10-15"

/**
 * Ratings over the contract (`ADR-0095` of the platform): the attentions still rateable, the ratings given, and rating
 * one. Stars and closed aspects only — the contract takes no free text — with the consent the sheet shows.
 *
 * A rateable attention names only its appointment, so it is joined with the patient's own appointments for the doctor
 * and the day (the contract says to). One that is not among them is left out rather than shown nameless.
 */
@OptIn(ExperimentalUuidApi::class)
internal class RemoteRatings(
    private val contract: ContractClient,
    private val appointments: AppointmentsSource,
    private val zones: PlaceZones = PlaceZones(),
) : RatingsGateway {
    /** One key per attention until it is rated: a retry of the same rating is answered with the one written. */
    private val clientIds = mutableMapOf<String, String>()

    override suspend fun ratingPrompts(): CareResult<List<RatingPrompt>> {
        val known = when (val read = appointments.appointments()) {
            is CareResult.Failed -> return read
            is CareResult.Ok -> read.value.associateBy { it.id }
        }
        return careRead {
            contract.call(ContractOperations.LIST_MY_RATEABLE_ATTENTIONS).items.mapNotNull { item ->
                known[item.appointmentId]?.let { RatingPrompt(it.id, it.doctor, it.date) }
            }
        }
    }

    override suspend fun sentRatingCount(): CareResult<Int> =
        careRead { contract.call(ContractOperations.LIST_MY_RATINGS).items.size }

    override suspend fun ratingFor(appointmentId: String): CareResult<SentRating?> = careRead {
        contract.call(ContractOperations.LIST_MY_RATINGS).items
            .firstOrNull { it.appointmentId == appointmentId }
            ?.let { SentRating(it.stars, wallTimeOf(it.createdAt, zones.fallback).date) }
    }

    override suspend fun rate(appointmentId: String, draft: RatingDraft): CareResult<Unit> {
        val clientId = clientIds.getOrPut(appointmentId) { Uuid.random().toString() }
        val sent = careRead {
            contract.call(
                ContractOperations.RATE_MY_ATTENTION,
                RateAttentionDto(
                    stars = draft.stars,
                    aspects = draft.aspects.map { RateAttentionDtoAspectsItem(it.wire) }.takeIf { it.isNotEmpty() },
                    clientId = clientId,
                    consent = true,
                    consentVersion = RATING_CONSENT_VERSION,
                ),
                path = mapOf("id" to appointmentId),
            )
            Unit
        }
        if (sent is CareResult.Ok) clientIds.remove(appointmentId)
        return sent
    }
}

/** The contract's name for an aspect: the same words, by construction of the closed list. */
private val RatingAspect.wire: String get() = name
