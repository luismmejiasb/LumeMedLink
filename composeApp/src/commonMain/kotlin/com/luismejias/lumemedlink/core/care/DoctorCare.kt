package com.luismejias.lumemedlink.core.care

import com.luismejias.lumemedlink.shared.CalendarDate
import com.luismejias.lumemedlink.shared.ChileanRut
import com.luismejias.lumemedlink.shared.ClockTime
import com.luismejias.lumemedlink.shared.EmailAddress
import com.luismejias.lumemedlink.shared.PhoneNumber

/**
 * What kind of visit an appointment is (m1): a CLOSED list, never free text — none of them says what the visit is about
 * (ADR-0001, the author's call of 2026-10-11). PROVISIONAL (ADR-0046): the contract has no such field yet.
 */
internal enum class VisitKind { FIRST_VISIT, FOLLOW_UP, TELECONSULT }

/** A patient as the doctor's contacts (m2): who they are and how to reach them — never their record (§1.0). */
internal data class PatientContact(
    val id: String,
    val givenNames: String,
    val familyNames: String,
    val rut: ChileanRut?,
    val mobile: PhoneNumber?,
    val email: EmailAddress?,
) {
    val displayName: String get() = "$givenNames $familyNames"

    val initials: String get() = "${givenNames.first()}${familyNames.first()}".uppercase()

    override fun toString(): String = "PatientContact(id=$id)"
}

/** One appointment on the doctor's simple agenda (m1). Never its clinical reason. */
internal data class AgendaEntry(
    val id: String,
    val patient: PatientContact,
    val date: CalendarDate,
    val start: ClockTime,
    val minutes: Int,
    val kind: VisitKind,
    val status: AppointmentStatus,
) {
    override fun toString(): String = "AgendaEntry(id=$id, status=$status)"
}

/** The doctor's contacts and how many patients they have attended in all (m2's «142 pacientes atendidos»). */
internal data class PatientRoster(val contacts: List<PatientContact>, val attendedCount: Int) {
    override fun toString(): String = "PatientRoster(contacts=${contacts.size})"
}

/** The doctor's public profile (m3) and the counts behind its rows. */
internal data class DoctorProfile(
    val doctor: CareDoctor,
    val linkedToLumeMed: Boolean,
    val address: String,
    val phone: String,
    /** «Compartir mi contacto»: one switch, for all their patients — the contract's rule. */
    val sharesContact: Boolean,
    val rating: Double?,
    val ratingCount: Int,
    val pendingCorrections: Int,
) {
    override fun toString(): String = "DoctorProfile(redacted)"
}

/**
 * Everything the doctor's three screens read (m1–m3). PROVISIONAL (ADR-0046), against a script.
 *
 * ⚠️ The agenda and the roster are the backend's TENANT-scoped routes of trap T11: a patient under a treatment
 * restriction (Ley 21.719) reappears on them unless the query excludes them. The contract request for each must carry
 * that question when this seam is wired to HTTP (constitution §1.2).
 */
internal interface DoctorCareGateway {
    fun today(): CalendarDate

    suspend fun profile(): CareResult<DoctorProfile>

    /** The day's appointments, confirmed and pending, in time order. */
    suspend fun agenda(day: CalendarDate): CareResult<List<AgendaEntry>>

    /** Requests the doctor has not answered yet (m1's «Solicitudes por confirmar»), soonest first. */
    suspend fun requests(): CareResult<List<AgendaEntry>>

    suspend fun confirm(entry: AgendaEntry): ChangeResult

    suspend fun reject(entry: AgendaEntry): ChangeResult

    suspend fun patients(): CareResult<PatientRoster>

    suspend fun shareContact(share: Boolean): CareResult<Unit>
}
