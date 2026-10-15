package com.luismejias.lumemedlink.core.care

/**
 * The ratings against the local platform before they are wired (the author, 2026-10-15: no design data where the app
 * talks to the platform). Every read is a failure, so each screen leaves its rating piece out (ADR-0067): no card on
 * Inicio, no count in Ajustes, no stars in an appointment's detail. Nothing the design invented is shown as if it were
 * the person's. A send fails as well, though no screen offers one.
 *
 * The contract has the four operations (`listMyRateableAttentions`, `rateMyAttention`, `listMyRatings`,
 * `withdrawMyRating`); wiring them waits on the author's call on the free text and the consent the contract requires.
 */
internal object UnwiredRatings : RatingsGateway {
    override suspend fun ratingPrompts(): CareResult<List<RatingPrompt>> = CareResult.Failed(CareFailure.Unavailable)

    override suspend fun sentRatingCount(): CareResult<Int> = CareResult.Failed(CareFailure.Unavailable)

    override suspend fun ratingFor(appointmentId: String): CareResult<SentRating?> =
        CareResult.Failed(CareFailure.Unavailable)

    override suspend fun rate(appointmentId: String, draft: RatingDraft): CareResult<Unit> =
        CareResult.Failed(CareFailure.Unavailable)
}
