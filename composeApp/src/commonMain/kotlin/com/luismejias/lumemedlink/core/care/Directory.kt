package com.luismejias.lumemedlink.core.care

import com.luismejias.lumemedlink.shared.CalendarDate
import com.luismejias.lumemedlink.shared.ClockTime

/*
 * PROVISIONAL — ADR-0046. Search to booking, read against contract 0.96.0: the catalogs
 * (`/v1/reference/specialties`, `/communes`), `/v1/directory/professionals/search`, its `availability`, and
 * `POST /v1/patient/appointments` with a `clientId`. What the contract lacks — ratings, attentions, synonyms,
 * nearby communes — is the design's and is listed in docs/backend-requests/0008.
 */

/** A specialty of the reference catalog. [synonyms] let «piel» find Dermatología (e1); no contract. */
internal data class Specialty(val code: String, val name: String, val synonyms: List<String> = emptyList())

/** A commune of the reference catalog, with its region's name for the grouping of e3. */
internal data class Commune(val code: String, val name: String, val region: String)

/** One free hour a professional published — exactly as availability returns it, never composed by the app. */
internal data class Slot(val date: CalendarDate, val start: ClockTime, val minutes: Int)

/**
 * A professional in the results (p3). [locationId] is where the search found them: booking names it.
 * [rating], [ratingCount] and [attentions] have no contract (request 0008 §4).
 */
internal data class DirectoryDoctor(
    val doctor: CareDoctor,
    val locationId: String,
    val commune: String,
    val place: CarePlace,
    val rating: Double?,
    val ratingCount: Int,
    val attentions: Int,
    val nextSlot: Slot?,
) {
    override fun toString(): String = "DirectoryDoctor(id=${doctor.id})"
}

/** A commune near the one searched that does have the specialty (e5). No contract. */
internal data class NearbyCommune(val commune: Commune, val doctors: Int, val firstDay: CalendarDate)

/** Why a booking did not go through. */
internal sealed interface BookingFailure {
    /** The hour was taken between reading and booking — the server re-verifies it (409). */
    data object SlotTaken : BookingFailure

    /** One pending request per professional at a time (409). */
    data object AlreadyPending : BookingFailure

    data object Offline : BookingFailure

    data object Unavailable : BookingFailure
}

internal sealed interface BookingResult {
    /** The appointment as the server holds it; `REQUESTED` until the practice confirms it. */
    data class Booked(val appointment: PatientAppointment) : BookingResult

    data class Failed(val failure: BookingFailure) : BookingResult
}

internal interface DirectoryGateway {
    suspend fun specialties(): CareResult<List<Specialty>>

    /** Only communes where the specialty has professionals ("Solo se listan comunas con médicos…", e3). */
    suspend fun communes(specialtyCode: String): CareResult<List<Commune>>

    suspend fun search(specialtyCode: String, communeCode: String): CareResult<List<DirectoryDoctor>>

    suspend fun nearbyCommunes(specialtyCode: String, communeCode: String): CareResult<List<NearbyCommune>>

    /** Free hours from [from] to [to], both included. */
    suspend fun availability(
        doctorId: String,
        locationId: String,
        from: CalendarDate,
        to: CalendarDate,
    ): CareResult<List<Slot>>

    /** [clientId] names THIS booking: a retry with it answers the same appointment, never a second one. */
    suspend fun book(doctorId: String, locationId: String, slot: Slot, clientId: String): BookingResult
}
