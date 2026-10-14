package com.luismejias.lumemedlink.core.care

import com.luismejias.lumemedlink.shared.CalendarDate
import com.luismejias.lumemedlink.shared.ChileanRut
import com.luismejias.lumemedlink.shared.ClockTime
import com.luismejias.lumemedlink.shared.EmailAddress
import com.luismejias.lumemedlink.shared.PhoneNumber

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
 * A professional as the patient sees them. [displayName] and [specialty] are the server's own words («Dra. Mariana
 * Rendón»: the contract's `professionalName`; «Medicina Familiar»: the specialty catalog), never translated or
 * rebuilt by the app. The specialty is shown after sign-in only (ADR-0063).
 */
internal data class CareDoctor(val id: String, val displayName: String, val specialty: String) {
    /** The design's parts, for what still has them (the script): «Dra.» «Mariana» «Rendón». */
    constructor(id: String, title: String, givenName: String, familyName: String, specialty: String) :
        this(id, listOf(title, givenName, familyName).filter { it.isNotBlank() }.joinToString(" "), specialty)

    /** The first letters of the first and last names, a title («Dr.», «Dra.») left out; «?» when there are none. */
    val initials: String get() = doctorInitials(displayName)

    override fun toString(): String = "CareDoctor(redacted)"
}

/** [CareDoctor.initials], pure for its test. */
internal fun doctorInitials(name: String): String {
    val words = name.split(' ').filter { it.isNotBlank() && !it.endsWith('.') }
    if (words.isEmpty()) return "?"
    val first = words.first().first()
    val last = words.last().first()
    return (if (words.size == 1) "$first" else "$first$last").uppercase()
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

/**
 * One appointment. Never its clinical reason — there is none to carry (§1.0, ADR-0001). [changeCutoffMinutes] is how
 * long before it the doctor still lets the patient change or cancel it — set per doctor in LumeMed (spec f2), and the
 * `cancelCutoffHours` the author approved for the contract (request 0008); minutes, because the design's is 30.
 */
internal data class PatientAppointment(
    val id: String,
    val doctor: CareDoctor,
    val date: CalendarDate,
    val start: ClockTime,
    val minutes: Int,
    val status: AppointmentStatus,
    val place: CarePlace,
    val locationId: String = "",
    val changeCutoffMinutes: Int = DAY_MINUTES,
) {
    override fun toString(): String = "PatientAppointment(id=$id, status=$status)"

    internal companion object {
        const val DAY_MINUTES = 24 * 60
    }
}

/** "¿Cómo fue tu atención?" (p1, f5): an attended appointment not yet rated. No contract (request 0008 §4). */
internal data class RatingPrompt(val appointmentId: String, val doctor: CareDoctor, val date: CalendarDate) {
    override fun toString(): String = "RatingPrompt(appointmentId=$appointmentId)"
}

/**
 * A rating the patient already sent for a visit (c3): the stars, their own words, and when. No contract (request 0008
 * §4). The words are the patient's; the author chose to show them (2026-10-11).
 */
internal data class SentRating(val stars: Int, val comment: String?, val sentOn: CalendarDate) {
    override fun toString(): String = "SentRating(stars=$stars)"
}

/**
 * What f5 asks about a visit, by how it went: what failed (1–2 stars), what to improve (3), what to highlight (4–5).
 * A closed list per level, plus «Otro» with the patient's own words. None names the visit's content (ADR-0001).
 */
internal enum class RatingAspect {
    // 1–2 stars: what failed.
    LONG_WAIT,
    NOT_EXPLAINED,
    UNKIND,
    UNEXPECTED_CHARGE,
    MOVED_HOUR,

    // 3 stars: what to improve, and 4–5: what to highlight.
    PUNCTUALITY,
    CLARITY,
    VISIT_TIME,
    NOTICE,
    FACILITIES,
    EXPLAINED_CLEARLY,
    KIND,
    SOLVED_IT,
}

/**
 * A rating the patient sends (f5). [other] is «Otro»'s short title (40 characters at most) and [comment]
 * «Cuéntanos más»: the patient's words, published without their name. PROVISIONAL (ADR-0046): the author chose the
 * design's free text for this mock; the contract the backend decided carries stars and closed reasons only (request
 * 0008).
 */
internal data class RatingDraft(
    val stars: Int,
    val aspects: Set<RatingAspect>,
    val other: String?,
    val comment: String?,
) {
    override fun toString(): String = "RatingDraft(stars=$stars, aspects=${aspects.size})"
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
    val mobile: PhoneNumber,
    val email: EmailAddress,
    val verified: Boolean,
) {
    val initials: String get() = "${givenNames.first()}${familyNames.first()}".uppercase()

    override fun toString(): String = "PatientProfile(redacted)"
}

/** How far ahead a patient may still change or cancel ("hasta 24 h antes", p5): the practice's rule. */
internal data class CarePolicy(val changeWindowHours: Int, val remindersHoursBefore: List<Int>)

/** Which of the profile's contacts a change replaces: the mobile (verified by SMS) or the email (by mail). */
internal enum class ContactKind { PHONE, EMAIL }

/** A code went out to the new contact: until when it holds, and when another may be asked for. */
internal data class ContactCodeDispatch(val expiresAtEpochMillis: Long, val resendAvailableAtEpochMillis: Long)

/** Why a contact change did not go through. */
internal sealed interface ContactChangeFailure {
    /** The code did not match; [attemptsLeft] before it is spent. */
    data class WrongCode(val attemptsLeft: Int) : ContactChangeFailure

    /** The code lapsed or was spent: a new one is needed. */
    data object CodeExpired : ContactChangeFailure

    data object Offline : ContactChangeFailure

    data object Unavailable : ContactChangeFailure
}

internal sealed interface ContactChangeResult<out T> {
    data class Ok<T>(val value: T) : ContactChangeResult<T>

    data class Failed(val failure: ContactChangeFailure) : ContactChangeResult<Nothing>
}

/** The clinic's wall calendar and clock: what every date and every change window is measured against. */
internal interface ClinicClock {
    /** Today on the clinic's wall calendar — the device does not get to say what day it is for a slot. */
    fun today(): CalendarDate

    /** The clinic's wall clock now: with [today], what a change window is measured against. */
    fun now(): ClockTime
}

/** The patient's profile, as the app shows it. */
internal interface PatientProfileSource {
    suspend fun profile(): CareResult<PatientProfile>
}

/** The patient's appointments, and the clinic's rules for changing them. */
internal interface AppointmentsSource {
    /** Upcoming and past, in date order. */
    suspend fun appointments(): CareResult<List<PatientAppointment>>

    suspend fun policy(): CareResult<CarePolicy>
}

/** Changing an appointment: moving, cancelling, withdrawing a request. */
internal interface AppointmentChanges {
    /**
     * Moves [appointment] to [slot] — the contract's ONE atomic operation, never a cancel plus a booking (T7).
     * [clientId] is this move's key, reused by every retry of it, so a dropped answer never makes a third write.
     */
    suspend fun reschedule(
        appointment: PatientAppointment,
        slot: Slot,
        reason: ChangeReason,
        clientId: String,
    ): ChangeResult

    /** Cancels a confirmed appointment; the contract requires a reason. */
    suspend fun cancel(appointment: PatientAppointment, reason: ChangeReason): ChangeResult

    /** Withdraws a request the practice has not answered: the contract's own verb, allowed at any time (spec f2). */
    suspend fun withdraw(appointment: PatientAppointment): ChangeResult
}

/** The professionals who saw the patient. */
internal interface MyDoctorsSource {
    suspend fun myDoctors(): CareResult<List<MyDoctor>>
}

/** Rating an attention, and what was rated. */
internal interface RatingsGateway {
    suspend fun ratingPrompts(): CareResult<List<RatingPrompt>>

    /** "3 enviadas" (p7). No contract (request 0008 §4). */
    suspend fun sentRatingCount(): CareResult<Int>

    /** The rating sent for an attended appointment, or null when there is none yet (c3 then offers the stars). */
    suspend fun ratingFor(appointmentId: String): CareResult<SentRating?>

    /** Sends a rating for an attended appointment (f5). */
    suspend fun rate(appointmentId: String, draft: RatingDraft): CareResult<Unit>
}

/** The patient's notices. */
internal interface NoticesSource {
    /** Whether something waits behind the bell (p1). No contract (request 0008 §5). */
    suspend fun hasUnreadNotices(): CareResult<Boolean>
}

/** Changing the phone or the e-mail, verified by a code before it lands. */
internal interface ContactChangeGateway {
    /**
     * Sends a code to [mobile], the number that would replace the profile's. Nothing changes until
     * [confirmContactChange].
     * No contract (PROVISIONAL, ADR-0046): the script plays it.
     */
    suspend fun requestPhoneChange(mobile: PhoneNumber): ContactChangeResult<ContactCodeDispatch>

    /** Sends a code to [email], the address that would replace the profile's; as [requestPhoneChange]. */
    suspend fun requestEmailChange(email: EmailAddress): ContactChangeResult<ContactCodeDispatch>

    /** A new code for the change pending, the old one spent. */
    suspend fun resendContactCode(): ContactChangeResult<ContactCodeDispatch>

    /** The pending change lands when [code] is the one sent; the profile then reads the new contact. */
    suspend fun confirmContactChange(code: String): ContactChangeResult<Unit>
}

/**
 * Every capability of the patient's side at once. Only what implements all of them names it: the script and the
 * composition root. A screen depends on the capabilities it uses, never on this (interface segregation, task 0036).
 */
internal interface PatientCareGateway :
    ClinicClock,
    PatientProfileSource,
    AppointmentsSource,
    AppointmentChanges,
    MyDoctorsSource,
    RatingsGateway,
    NoticesSource,
    ContactChangeGateway
