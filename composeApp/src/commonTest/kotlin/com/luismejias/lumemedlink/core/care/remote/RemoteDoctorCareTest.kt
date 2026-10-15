package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.AgendaEntry
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.ChangeFailure
import com.luismejias.lumemedlink.core.care.ChangeResult
import com.luismejias.lumemedlink.core.care.DoctorProfile
import com.luismejias.lumemedlink.core.care.PatientRoster
import com.luismejias.lumemedlink.core.care.VisitKind
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.NO_SECURITY_EVENTS
import com.luismejias.lumemedlink.core.networking.NetworkLogEntry
import com.luismejias.lumemedlink.core.networking.NetworkLogSink
import com.luismejias.lumemedlink.core.networking.lumeHttpClient
import com.luismejias.lumemedlink.shared.CalendarDate
import com.luismejias.lumemedlink.shared.ClockTime
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private class DoctorLogs : NetworkLogSink {
    override fun log(entry: NetworkLogEntry) = Unit
}

// Synthetic, every value (§9).
private const val ME = """{"userId":"u-1","email":"dra@clinica.test","phone":"+56900000000","platformRole":null,
    "recordWindows":{"closureHours":24,"addendumHours":72},"memberships":[{"tenantId":"t-1","tenantName":"Clínica Sintética","roles":["DOCTOR"],
    "profileId":"p-1"}],"profile":{"id":"p-1","rut":null,"givenName":"Ana","familyName":"Sintética",
    "secondFamilyName":null,"professionalTitle":"Médica Cirujana","specialtyCode":"CARDIOLOGIA"}}"""

private const val SPECIALTIES = """[{"code":"CARDIOLOGIA","name":"Cardiología","profession":"MEDICINE","nidCode":null,
    "isSuppressed":false}]"""

private fun card(id: String, status: String, channel: String, startsAt: String, endsAt: String) =
    """{"id":"$id","patientId":"pt-$id","patientGivenName":"Camila","patientFamilyInitial":"S","professionalId":"p-1",
    "startsAt":"$startsAt","endsAt":"$endsAt","status":"$status","channel":"$channel","locationLabel":null,
    "specialtyCode":null,"requestExpiresAt":null}"""

private val AGENDA = """{"timeZone":"America/Santiago","items":[""" +
    card("a-2", "CONFIRMED", "IN_PERSON", "2026-10-15T14:00:00.000Z", "2026-10-15T14:30:00.000Z") + "," +
    card("a-1", "CONFIRMED", "TELEMEDICINE", "2026-10-15T13:00:00.000Z", "2026-10-15T13:20:00.000Z") + "," +
    card("a-3", "CANCELLED", "IN_PERSON", "2026-10-15T15:00:00.000Z", "2026-10-15T15:30:00.000Z") + "," +
    card("a-4", "REQUESTED", "IN_PERSON", "2026-10-15T16:00:00.000Z", "2026-10-15T16:30:00.000Z") + "]}"

private fun contact(id: String, given: String, social: String?) =
    """{"patientId":"$id","givenName":"$given","familyName":"Soto","secondFamilyName":null,
    "socialName":${social?.let { "\"$it\"" } ?: "null"},"phone":"+56912345678","email":null,"insuranceKind":null,
    "insuranceName":null}"""

/** The platform's doctor views, scripted, and the queries they were asked with. */
private class DoctorPlatform {
    val asked = mutableListOf<Url>()

    fun care(): RemoteDoctorCare {
        val engine = MockEngine { request ->
            asked += request.url
            val path = request.url.encodedPath
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                path == "/v1/me" -> respond(ME, HttpStatusCode.OK, json)
                path.endsWith("/specialties") -> respond(SPECIALTIES, HttpStatusCode.OK, json)
                path.endsWith("/agenda-cards") -> respond(AGENDA, HttpStatusCode.OK, json)
                path.endsWith("/patient-contact-cards") && request.url.parameters["cursor"] == null ->
                    respond(
                        """{"items":[${contact("c-2", "Diego", null)}],"nextCursor":"next"}""",
                        HttpStatusCode.OK,
                        json,
                    )
                path.endsWith("/patient-contact-cards") ->
                    respond(
                        """{"items":[${contact("c-1", "Antonia", "Toña")}],"nextCursor":null}""",
                        HttpStatusCode.OK,
                        json,
                    )
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val contract = ContractClient(
            lumeHttpClient("https://api.test.lume", engine, DoctorLogs(), securityEvents = NO_SECURITY_EVENTS),
        )
        return RemoteDoctorCare(contract, SpecialtyNames(contract), TimeZone.of("America/Santiago"))
    }
}

/** The doctor's side over the two non-clinical views (ADR-0061's amendment): who and when, never the act. */
class RemoteDoctorCareTest {
    @Test
    fun theDaysAgendaIsTheDoctorsOwnAtTheirClinicOnItsClockWithoutCancelledOrRequested() = runTest {
        val platform = DoctorPlatform()
        val day = CalendarDate(2026, 10, 15)
        val agenda = assertIs<CareResult.Ok<List<AgendaEntry>>>(platform.care().agenda(day)).value
        assertEquals(listOf("a-1", "a-2"), agenda.map { it.id }, "in time order, the cancelled and the request out")
        assertEquals(ClockTime(10, 0), agenda.first().start, "13:00Z is 10:00 in Santiago")
        assertEquals(20, agenda.first().minutes)
        assertEquals(VisitKind.TELECONSULT, agenda.first().kind)
        assertNull(agenda.last().kind, "in person says nothing about first visit or follow-up")
        assertEquals("Camila S.", agenda.first().patient.displayName)
        val query = platform.asked.first { it.encodedPath.endsWith("/agenda-cards") }
        assertEquals("/v1/orgs/t-1/agenda-cards", query.encodedPath)
        assertEquals("p-1", query.parameters["professionalId"])
    }

    @Test
    fun theRequestsAreAskedForByTheirStatus() = runTest {
        val platform = DoctorPlatform()
        platform.care().requests()
        val query = platform.asked.first { it.encodedPath.endsWith("/agenda-cards") }
        assertEquals("REQUESTED", query.parameters["status"])
    }

    @Test
    fun theRosterReadsEveryPageAndUsesTheNameThePatientAskedFor() = runTest {
        val roster = assertIs<CareResult.Ok<PatientRoster>>(DoctorPlatform().care().patients()).value
        assertEquals(listOf("Diego Soto", "Toña Soto"), roster.contacts.map { it.displayName })
        assertNull(roster.attendedCount, "the platform does not count them, so nothing is shown")
        assertNull(roster.contacts.first().visits)
    }

    @Test
    fun theProfileIsTheDoctorsNameAndSpecialtyAndLeavesOutWhatItDoesNotKnow() = runTest {
        val profile = assertIs<CareResult.Ok<DoctorProfile>>(DoctorPlatform().care().profile()).value
        assertEquals("Ana Sintética" to "Cardiología", profile.doctor.displayName to profile.doctor.specialty)
        assertNull(profile.address)
        assertNull(profile.phone)
        assertNull(profile.sharesContact)
        assertNull(profile.pendingCorrections)
    }

    @Test
    fun whatTheAllowlistDoesNotReachFailsInsteadOfPretending() = runTest {
        val care = DoctorPlatform().care()
        val entry = assertIs<CareResult.Ok<List<AgendaEntry>>>(care.agenda(CalendarDate(2026, 10, 15))).value.first()
        assertEquals(ChangeResult.Failed(ChangeFailure.Unavailable), care.confirm(entry))
        assertEquals(ChangeResult.Failed(ChangeFailure.Unavailable), care.reject(entry))
        assertIs<CareResult.Failed>(care.shareContact(true))
    }
}
