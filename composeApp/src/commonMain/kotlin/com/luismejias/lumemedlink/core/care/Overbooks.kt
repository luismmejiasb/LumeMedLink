package com.luismejias.lumemedlink.core.care

import com.luismejias.lumemedlink.shared.CalendarDate

/*
 * Asking a practice for an overbook (ADR-0069, the platform's `ADR-0105`): asked, never offered. The app never shows an
 * overbook as a bookable hour; it lets the patient ask a professional who opened it, and the practice answers with an
 * hour, which arrives as any booking does, or with a «no».
 */

/** How urgent the patient says it is. The practice may correct it while the request waits. */
internal enum class OverbookUrgency { URGENT, PRIORITY, NORMAL }

/**
 * Why the patient asks: the platform's closed, NON-CLINICAL list (ADR-0069 point 2). Shown only on the sheet where it
 * is picked — never in a list, a notice, a push, a log or an analytics event. [RENEW_PRESCRIPTION] asks for an hour; it
 * moves no prescription (ADR-0007).
 */
internal enum class OverbookReason { EARLIER_FOLLOW_UP, REVIEW_RESULTS, RENEW_PRESCRIPTION, OTHER }

/** The contract's open vocabulary, folded: a value this app does not know is [OTHER], shown without a verdict. */
internal enum class OverbookRequestStatus { PENDING, GRANTED, DECLINED, WITHDRAWN, OTHER }

/**
 * One request, as the patient's list shows it: who, where, how it stands and when it was asked. Never its reason or
 * its urgency (ADR-0069 point 2). [doctor] is null when the directory no longer names the professional.
 */
internal data class OverbookRequest(
    val id: String,
    val doctor: CareDoctor?,
    /** The clinic, by the name of the patient's profile there; empty when the app cannot name it. */
    val clinic: String,
    val status: OverbookRequestStatus,
    val askedOn: CalendarDate,
    /** The practice recorded it from a call, rather than the patient asking from the app. */
    val byPhone: Boolean,
) {
    val waiting: Boolean get() = status == OverbookRequestStatus.PENDING

    override fun toString(): String = "OverbookRequest(id=$id, status=$status)"
}

/** What the patient asks for, from the sheet. */
internal data class OverbookDraft(val doctorId: String, val urgency: OverbookUrgency, val reason: OverbookReason) {
    override fun toString(): String = "OverbookDraft(redacted)"
}

/** Why asking or withdrawing did not go through. */
internal sealed interface OverbookFailure {
    /** The professional does not take requests (anymore), or not from this patient: the platform's 404, no more. */
    data object NotTaken : OverbookFailure

    /** One request waits per professional (409 `pending_request_exists`). */
    data object AlreadyWaiting : OverbookFailure

    /** It no longer waits: the practice answered it first (409 `conflict`). */
    data object NoLongerWaiting : OverbookFailure

    data object Offline : OverbookFailure

    data object Unavailable : OverbookFailure
}

internal sealed interface OverbookResult {
    /** The request as the platform holds it. */
    data class Done(val request: OverbookRequest) : OverbookResult

    data class Failed(val failure: OverbookFailure) : OverbookResult
}

/** The patient's overbook requests: asking, the list, and taking one back. */
internal interface OverbookRequests {
    /** The patient's, newest first, at every clinic, including what a practice recorded from a call. */
    suspend fun overbookRequests(): CareResult<List<OverbookRequest>>

    /**
     * Asks for [draft]. [clientId] names THIS attempt: a retry with it answers the request already made, never a
     * second one; what is sent changing renews it.
     */
    suspend fun requestOverbook(draft: OverbookDraft, clientId: String): OverbookResult

    /** Takes back a request that still waits. */
    suspend fun withdrawOverbookRequest(request: OverbookRequest): OverbookResult
}
