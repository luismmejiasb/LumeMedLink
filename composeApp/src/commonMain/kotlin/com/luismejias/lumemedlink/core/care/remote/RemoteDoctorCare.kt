package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.AgendaEntry
import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.CareDoctor
import com.luismejias.lumemedlink.core.care.CareFailure
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.ChangeFailure
import com.luismejias.lumemedlink.core.care.ChangeResult
import com.luismejias.lumemedlink.core.care.DoctorCareGateway
import com.luismejias.lumemedlink.core.care.DoctorProfile
import com.luismejias.lumemedlink.core.care.PatientContact
import com.luismejias.lumemedlink.core.care.PatientRoster
import com.luismejias.lumemedlink.core.care.VisitKind
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.contract.AgendaCardsDtoItemsItem
import com.luismejias.lumemedlink.core.networking.contract.ContractOperations
import com.luismejias.lumemedlink.core.networking.contract.PatientContactCardPageDtoItemsItem
import com.luismejias.lumemedlink.shared.CalendarDate
import com.luismejias.lumemedlink.shared.ClockTime
import com.luismejias.lumemedlink.shared.EmailAddress
import com.luismejias.lumemedlink.shared.PhoneNumber
import kotlinx.datetime.TimeZone

/** The practice a doctor acts in, read once from `/v1/me`: its id and the doctor's professional profile there. */
private class Practice(val tenantId: String, val profileId: String, val doctor: CareDoctor)

/**
 * The doctor's side over the contract (ADR-0061's amendment of 2026-10-15): the two non-clinical views the platform
 * built for LumeMedLink — the agenda cards and the patient contact cards — and the doctor's own name from `/v1/me`.
 * Never the ficha: the cards carry who, when, where and in what state, and nothing about the act (§1.0). A patient
 * under a restriction of processing is absent from both, by the platform (trap T11).
 *
 * What the allowlist does not reach is said, never simulated: confirming or rejecting a request and sharing the contact
 * fail, and the profile leaves out what `/v1/me` does not carry (ADR-0067).
 */
internal class RemoteDoctorCare(
    private val contract: ContractClient,
    private val specialties: SpecialtyNames,
    private val zone: TimeZone = PlaceZones().fallback,
    private val clock: ClinicWallClock = ClinicWallClock(zone),
) : DoctorCareGateway {
    /** Read once on success; a failure is read again next time. */
    private val practice = ReadOnce<Practice?>(null) {
        val me = contract.call(ContractOperations.ME)
        val membership = me.memberships.firstOrNull { "DOCTOR" in it.roles && it.profileId != null }
        val profile = me.profile
        if (membership == null || profile == null) {
            null
        } else {
            val names = specialties.all()
            Practice(
                tenantId = membership.tenantId,
                profileId = membership.profileId.orEmpty(),
                doctor = CareDoctor(
                    id = membership.profileId.orEmpty(),
                    displayName = professionalDisplayName(profile.givenName, profile.familyName),
                    specialty = profile.specialtyCode?.let(names::get).orEmpty(),
                ),
            )
        }
    }

    override fun today(): CalendarDate = clock.today()

    override suspend fun profile(): CareResult<DoctorProfile> = withPractice { here ->
        CareResult.Ok(
            DoctorProfile(
                doctor = here.doctor,
                linkedToLumeMed = true,
                address = null,
                phone = null,
                sharesContact = null,
                rating = null,
                ratingCount = 0,
                pendingCorrections = null,
            ),
        )
    }

    override suspend fun agenda(day: CalendarDate): CareResult<List<AgendaEntry>> = withPractice { here ->
        careRead {
            cards(here, day, day.plusDays(1)).filter {
                it.status != AppointmentStatus.CANCELLED && it.status != AppointmentStatus.REQUESTED
            }
        }
    }

    override suspend fun requests(): CareResult<List<AgendaEntry>> = withPractice { here ->
        val today = clock.today()
        careRead { cards(here, today, today.plusDays(REQUEST_WINDOW_DAYS), status = "REQUESTED") }
    }

    override suspend fun confirm(entry: AgendaEntry): ChangeResult = ChangeResult.Failed(ChangeFailure.Unavailable)

    override suspend fun reject(entry: AgendaEntry): ChangeResult = ChangeResult.Failed(ChangeFailure.Unavailable)

    override suspend fun patients(): CareResult<PatientRoster> = withPractice { here ->
        careRead {
            val contacts = mutableListOf<PatientContact>()
            var cursor: String? = null
            do {
                val page = contract.call(
                    ContractOperations.LIST_PATIENT_CONTACT_CARDS,
                    path = mapOf("tenantId" to here.tenantId),
                    query = listOfNotNull(cursor?.let { "cursor" to it }).toMap(),
                )
                contacts += page.items.map { it.toContact() }
                cursor = page.nextCursor
            } while (cursor != null && contacts.size < MAX_CONTACTS)
            PatientRoster(contacts.sortedBy { it.displayName }, attendedCount = null)
        }
    }

    override suspend fun shareContact(share: Boolean): CareResult<Unit> = CareResult.Failed(CareFailure.Unavailable)

    private suspend fun cards(
        here: Practice,
        from: CalendarDate,
        to: CalendarDate,
        status: String? = null,
    ): List<AgendaEntry> {
        val answer = contract.call(
            ContractOperations.LIST_AGENDA_CARDS,
            path = mapOf("tenantId" to here.tenantId),
            query = listOfNotNull(
                "professionalId" to here.profileId,
                "from" to instantOf(from, ClockTime(0, 0), zone),
                "to" to instantOf(to, ClockTime(0, 0), zone),
                status?.let { "status" to it },
            ).toMap(),
        )
        val cardZone = zoneOf(answer.timeZone, zone)
        return answer.items.map { it.toEntry(cardZone) }.sortedWith(compareBy({ it.date }, { it.start }))
    }

    private suspend fun <T> withPractice(read: suspend (Practice) -> CareResult<T>): CareResult<T> {
        val here = practice.get() ?: return CareResult.Failed(CareFailure.Unavailable)
        return read(here)
    }

    private companion object {
        /** The platform answers at most 62 days of agenda (`listAgendaCards`). */
        const val REQUEST_WINDOW_DAYS = 62

        /** A page past this is not a phone's list: the roster stops rather than read without end. */
        const val MAX_CONTACTS = 500
    }
}

private fun AgendaCardsDtoItemsItem.toEntry(zone: TimeZone): AgendaEntry {
    val wall = wallTimeOf(startsAt, zone)
    return AgendaEntry(
        id = id,
        patient = PatientContact(
            id = patientId,
            givenNames = patientGivenName,
            familyNames = "$patientFamilyInitial.",
            rut = null,
            mobile = null,
            email = null,
        ),
        date = wall.date,
        start = wall.time,
        minutes = minutesBetween(startsAt, endsAt),
        kind = if (channel == "TELEMEDICINE") VisitKind.TELECONSULT else null,
        status = appointmentStatus(status),
    )
}

private fun PatientContactCardPageDtoItemsItem.toContact(): PatientContact = PatientContact(
    id = patientId,
    // The name the patient asked to be called by, when they gave one.
    givenNames = socialName ?: givenName,
    familyNames = listOfNotNull(familyName, secondFamilyName).joinToString(" "),
    rut = null,
    mobile = phone?.let(PhoneNumber::stored),
    email = email?.let(EmailAddress::parse),
)
