package com.luismejias.lumemedlink.core.care

import com.luismejias.lumemedlink.shared.CalendarDate
import com.luismejias.lumemedlink.shared.ChileanMobile
import com.luismejias.lumemedlink.shared.ChileanRut
import com.luismejias.lumemedlink.shared.ClockTime
import com.luismejias.lumemedlink.shared.EmailAddress

/*
 * PROVISIONAL — ADR-0046. The patient's main app has the DESIGN's shape (tareas/PENDING/Handoff,
 * 2-App-principal), read against contract 0.96.0 of the backend. Where the contract already has the datum
 * (`/v1/patient/appointments`, `/v1/patient/professional-contacts`, `/v1/patient/profiles`, the reference
 * catalogs) the field mirrors it; where it does not, the field is the design's and it is listed in
 * docs/backend-requests/0008. Nothing here is a session: the access flow begins none (ADR-0044).
 */

/** Why a read did not go through. One state of the design each. */
internal sealed interface CareFailure {
    /** No network: the design's "Sin conexión" with "Reintentar" (e6). */
    data object Offline : CareFailure

    /** The server answered something this app cannot use — never shown as a code. */
    data object Unavailable : CareFailure
}

internal sealed interface CareResult<out T> {
    data class Ok<T>(val value: T) : CareResult<T>

    data class Failed(val failure: CareFailure) : CareResult<Nothing>
}

/**
 * A professional as the patient sees them. [title] and [specialty] are the server's own words ("Dra.",
 * "Medicina Familiar": `professionalTitle` and the specialty catalog), never translated by the app.
 */
internal data class CareDoctor(
    val id: String,
    val title: String,
    val givenName: String,
    val familyName: String,
    val specialty: String,
) {
    val displayName: String get() = "$title $givenName $familyName"

    val initials: String get() = "${givenName.first()}${familyName.first()}".uppercase()

    override fun toString(): String = "CareDoctor(redacted)"
}

/**
 * Where an appointment happens, in the design's two lines: "Av. Providencia 1208, oficina 503" and "Centro
 * Médico Lyon · Providencia, Santiago". The contract has one `locationLabel` and the clinic's name — no
 * street, no coordinates (request 0008 §2).
 */
internal data class CarePlace(val address: String, val detail: String) {
    override fun toString(): String = "CarePlace(redacted)"
}

/**
 * The contract's open vocabulary, folded into what the design draws. `SCHEDULED` (booked without review)
 * and `CONFIRMED` both read "Confirmada"; a value this app does not know is [OTHER], shown without a verdict.
 */
internal enum class AppointmentStatus { REQUESTED, CONFIRMED, COMPLETED, CANCELLED, OTHER }

/** One appointment. Never its clinical reason — there is none to carry (§1.0, ADR-0001). */
internal data class PatientAppointment(
    val id: String,
    val doctor: CareDoctor,
    val date: CalendarDate,
    val start: ClockTime,
    val minutes: Int,
    val status: AppointmentStatus,
    val place: CarePlace,
) {
    override fun toString(): String = "PatientAppointment(id=$id, status=$status)"
}

/** "¿Cómo fue tu atención?" (p1, f5): an attended appointment not yet rated. No contract (request 0008 §4). */
internal data class RatingPrompt(val appointmentId: String, val doctor: CareDoctor, val date: CalendarDate) {
    override fun toString(): String = "RatingPrompt(appointmentId=$appointmentId)"
}

/**
 * A professional who attended the patient (p6). The contact is null when they do not share it — the
 * contract's rule, once for all their patients. [attentions] has no contract field (request 0008 §3).
 */
internal data class MyDoctor(val doctor: CareDoctor, val attentions: Int, val phone: String?, val email: String?) {
    val sharesContact: Boolean get() = phone != null || email != null

    override fun toString(): String = "MyDoctor(attentions=$attentions)"
}

/** The account holder (p7). The contract has a profile PER CLINIC and no RUT in it (request 0008 §1). */
internal data class PatientProfile(
    val givenNames: String,
    val familyNames: String,
    val rut: ChileanRut,
    val mobile: ChileanMobile,
    val email: EmailAddress,
    val verified: Boolean,
) {
    val initials: String get() = "${givenNames.first()}${familyNames.first()}".uppercase()

    override fun toString(): String = "PatientProfile(redacted)"
}

/** How far ahead a patient may still change or cancel ("hasta 24 h antes", p5): the practice's rule. */
internal data class CarePolicy(val changeWindowHours: Int, val remindersHoursBefore: List<Int>)

/** Everything the five tab roots read. */
internal interface PatientCareGateway {
    /** Today on the clinic's wall calendar — the device does not get to say what day it is for a slot. */
    fun today(): CalendarDate

    suspend fun profile(): CareResult<PatientProfile>

    /** Upcoming and past, in date order. */
    suspend fun appointments(): CareResult<List<PatientAppointment>>

    suspend fun ratingPrompts(): CareResult<List<RatingPrompt>>

    suspend fun myDoctors(): CareResult<List<MyDoctor>>

    suspend fun policy(): CareResult<CarePolicy>

    /** "3 enviadas" (p7). No contract (request 0008 §4). */
    suspend fun sentRatingCount(): CareResult<Int>

    /** Whether something waits behind the bell (p1). No contract (request 0008 §5). */
    suspend fun hasUnreadNotices(): CareResult<Boolean>
}
