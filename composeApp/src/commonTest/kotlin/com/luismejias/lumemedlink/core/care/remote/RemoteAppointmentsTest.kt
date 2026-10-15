package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.CareFailure
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.ChangeFailure
import com.luismejias.lumemedlink.core.care.ChangeReason
import com.luismejias.lumemedlink.core.care.ChangeResult
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.Slot
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.NO_SECURITY_EVENTS
import com.luismejias.lumemedlink.core.networking.NetworkLogEntry
import com.luismejias.lumemedlink.core.networking.NetworkLogSink
import com.luismejias.lumemedlink.core.networking.lumeHttpClient
import com.luismejias.lumemedlink.core.networking.noNetworkFailure
import com.luismejias.lumemedlink.core.networking.refusedConnectionFailure
import com.luismejias.lumemedlink.shared.CalendarDate
import com.luismejias.lumemedlink.shared.ClockTime
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val SANTIAGO = TimeZone.of("America/Santiago")
private val JSON = headersOf(HttpHeaders.ContentType, "application/json")
private val PROBLEM = headersOf(HttpHeaders.ContentType, "application/problem+json")

private class Silent : NetworkLogSink {
    override fun log(entry: NetworkLogEntry) = Unit
}

/** One appointment as the contract writes it, every required field present. Synthetic (§9). */
private fun appointmentJson(
    id: String,
    startsAt: String,
    endsAt: String,
    status: String,
    specialtyCode: String? = "MEDICINA_FAMILIAR",
    professionalName: String? = "Dra. Ana Sintética",
    locationId: String = "null",
): String {
    fun q(value: String?) = value?.let { "\"$it\"" } ?: "null"
    return """{"id":"$id","clinicName":"Centro Sintético","professionalName":${q(professionalName)},
        "startsAt":"$startsAt","endsAt":"$endsAt","status":"$status","channel":"IN_PERSON",
        "locationLabel":"Sede Norte","specialtyCode":${q(specialtyCode)},"professionalId":"pro-1",
        "locationAddress":{"street":"Calle Falsa","streetNumber":"123","unit":null,"communeCode":null,
        "regionCode":null,"postalCode":null},
        "cancelCutoffHours":24,"closedAt":null,"cancellationReason":null,"cancelledBy":null,"cancelledAt":null,
        "rescheduledFromId":null,"rescheduledBy":null,"rescheduleReason":null,"requestExpiresAt":null,
        "requestedBy":null,"confirmedBy":null,"confirmedAt":null,"locationId":$locationId,
        "timeZone":"America/Santiago"}"""
}

private class Backend(
    var appointments: String = "[]",
    var specialties: Pair<HttpStatusCode, String> =
        HttpStatusCode.OK to """[{"code":"MEDICINA_FAMILIAR","name":"Medicina Familiar","profession":"MEDICINE",""" +
            """"nidCode":null,"isSuppressed":false}]""",
    var change: Pair<HttpStatusCode, String> = HttpStatusCode.NoContent to "",
    var offline: Boolean = false,
    var down: Boolean = false,
) {
    val requests = mutableListOf<Pair<String, String>>()

    suspend fun MockRequestHandleScope.answer(request: HttpRequestData): HttpResponseData {
        val path = request.url.encodedPath
        requests += (request.method.value + " " + path) to request.body.toByteArray().decodeToString()
        if (offline) throw noNetworkFailure()
        if (down) throw refusedConnectionFailure()
        return when {
            path.endsWith("/v1/reference/specialties") -> respond(specialties.second, specialties.first, JSON)
            path.endsWith("/v1/patient/appointments") -> respond(appointments, HttpStatusCode.OK, JSON)
            else -> respond(change.second, change.first, if (change.first.value >= 400) PROBLEM else JSON)
        }
    }

    fun gateway(): RemoteAppointments {
        val engine = MockEngine { request -> answer(request) }
        val contract = ContractClient(
            lumeHttpClient("https://api.test.lume", engine, Silent(), securityEvents = NO_SECURITY_EVENTS),
        )
        return RemoteAppointments(contract, SpecialtyNames(contract), PlaceZones(SANTIAGO))
    }
}

private fun appointment(id: String = "a-1", locationId: String = "") = PatientAppointment(
    id = id,
    doctor = com.luismejias.lumemedlink.core.care.CareDoctor("pro-1", "Dra. Ana Sintética", "Medicina Familiar"),
    date = CalendarDate(2026, 10, 15),
    start = ClockTime(10, 30),
    minutes = 30,
    status = AppointmentStatus.CONFIRMED,
    place = com.luismejias.lumemedlink.core.care.CarePlace("", ""),
    locationId = locationId,
)

class RemoteAppointmentsTest {
    @Test
    fun anHourIsShownOnTheClinicsWallClockInSummerAndInWinter() = runTest {
        val backend = Backend(
            appointments =
            "[" + appointmentJson("a-oct", "2026-10-15T13:30:00.000Z", "2026-10-15T14:00:00.000Z", "SCHEDULED") +
                "," + appointmentJson("a-jun", "2026-06-15T14:30:00.000Z", "2026-06-15T15:15:00.000Z", "CONFIRMED") +
                "]",
        )

        val list = assertIs<CareResult.Ok<List<PatientAppointment>>>(backend.gateway().appointments()).value

        val june = list.first { it.id == "a-jun" }
        val october = list.first { it.id == "a-oct" }
        assertEquals(CalendarDate(2026, 6, 15) to ClockTime(10, 30), june.date to june.start, "UTC-4 in June")
        assertEquals(CalendarDate(2026, 10, 15) to ClockTime(10, 30), october.date to october.start, "UTC-3 in October")
        assertEquals(45, june.minutes)
        assertEquals(listOf("a-jun", "a-oct"), list.map { it.id }, "in date order")
    }

    @Test
    fun theOpenStatusVocabularyFoldsIntoTheDesignsAndAnUnknownWordIsNoError() = runTest {
        val statuses =
            listOf("REQUESTED", "SCHEDULED", "CONFIRMED", "COMPLETED", "CANCELLED", "NO_SHOW", "SOMETHING_NEW")
        val backend = Backend(
            appointments = statuses.mapIndexed { i, s ->
                appointmentJson("a-$i", "2026-10-1${i}T13:30:00.000Z", "2026-10-1${i}T14:00:00.000Z", s)
            }.joinToString(",", "[", "]"),
        )

        val list = assertIs<CareResult.Ok<List<PatientAppointment>>>(backend.gateway().appointments()).value

        assertEquals(
            listOf(
                AppointmentStatus.REQUESTED,
                AppointmentStatus.CONFIRMED,
                AppointmentStatus.CONFIRMED,
                AppointmentStatus.COMPLETED,
                AppointmentStatus.CANCELLED,
                AppointmentStatus.OTHER,
                AppointmentStatus.OTHER,
            ),
            list.map { it.status },
        )
    }

    @Test
    fun theSpecialtyIsTheCatalogsNameAndACatalogThatFailsLeavesItBlankNotTheList() = runTest {
        val one =
            "[" + appointmentJson("a-1", "2026-10-15T13:30:00.000Z", "2026-10-15T14:00:00.000Z", "SCHEDULED") + "]"
        val named =
            assertIs<CareResult.Ok<List<PatientAppointment>>>(Backend(appointments = one).gateway().appointments())
        assertEquals("Medicina Familiar", named.value.single().doctor.specialty)

        listOf(HttpStatusCode.InternalServerError to "", HttpStatusCode.OK to """[{"code":"X"}]""").forEach { answer ->
            val broken = Backend(appointments = one, specialties = answer)
            val unnamed = assertIs<CareResult.Ok<List<PatientAppointment>>>(broken.gateway().appointments())
            assertEquals("", unnamed.value.single().doctor.specialty, "catalog answered ${answer.first}")
        }
    }

    @Test
    fun noNetworkReadsAsOfflineAndAServerFailureAsUnavailable() = runTest {
        assertEquals(CareResult.Failed(CareFailure.Offline), Backend(offline = true).gateway().appointments())
        assertEquals(CareResult.Failed(CareFailure.Unavailable), Backend(down = true).gateway().appointments())
        val failing = Backend(specialties = HttpStatusCode.OK to "[]", appointments = "not json")
        assertEquals(CareResult.Failed(CareFailure.Unavailable), failing.gateway().appointments())
    }

    @Test
    fun aCancelSendsTheReasonInTheContractsWords() = runTest {
        val backend = Backend()

        val result = backend.gateway().cancel(appointment("a-9"), ChangeReason.SCHEDULE_CONFLICT)

        assertEquals(ChangeResult.Done, result)
        assertEquals(
            "POST /v1/patient/appointments/a-9/cancel" to """{"cancellationReason":"SCHEDULE_CONFLICT"}""",
            backend.requests.last(),
        )
    }

    @Test
    fun aClosedWindowIsTheDesignsWindowClosed() = runTest {
        val backend = Backend(
            change = HttpStatusCode.Conflict to """{"type":"/problems/cancellation_window_closed","status":409}""",
        )
        assertEquals(
            ChangeResult.Failed(ChangeFailure.WindowClosed),
            backend.gateway().cancel(appointment(), ChangeReason.OTHER),
        )
    }

    @Test
    fun aWithdrawalSendsTheNeutralReasonTheContractRequires() = runTest {
        val backend = Backend()
        assertEquals(ChangeResult.Done, backend.gateway().withdraw(appointment("a-3")))
        assertEquals(
            "POST /v1/patient/appointments/a-3/withdraw" to """{"cancellationReason":"OTHER"}""",
            backend.requests.last(),
        )
    }

    @Test
    fun aMoveWithoutItsPlaceIsRefusedWithoutCallingTheServer() = runTest {
        val backend = Backend()
        val slot = Slot(CalendarDate(2026, 10, 20), ClockTime(9, 0), 30)

        val result = backend.gateway().reschedule(appointment(locationId = ""), slot, ChangeReason.OTHER, "c-1")

        assertEquals(ChangeResult.Failed(ChangeFailure.Unavailable), result)
        assertTrue(backend.requests.isEmpty(), "nothing was sent")
    }

    @Test
    fun aMoveSendsTheNewHourAsUtcInstantsAndOneKey() = runTest {
        val moved = appointmentJson("a-new", "2026-10-20T12:00:00.000Z", "2026-10-20T12:30:00.000Z", "REQUESTED")
        val backend = Backend(change = HttpStatusCode.OK to moved)
        val slot = Slot(CalendarDate(2026, 10, 20), ClockTime(9, 0), 30)

        val result = backend.gateway().reschedule(
            appointment(locationId = "loc-1"),
            slot,
            ChangeReason.CANNOT_ATTEND,
            "c-1",
        )

        assertEquals("a-new", assertIs<ChangeResult.Moved>(result).appointment.id)
        assertEquals(
            "POST /v1/patient/appointments/a-1/reschedule" to
                """{"locationId":"loc-1","startsAt":"2026-10-20T12:00:00Z","endsAt":"2026-10-20T12:30:00Z",""" +
                """"rescheduleReason":"CANNOT_ATTEND","clientId":"c-1"}""",
            backend.requests.single { it.first.endsWith("/reschedule") },
        )
    }

    @Test
    fun theDoctorsInitialsLeaveTheTitleOut() {
        assertEquals("AS", com.luismejias.lumemedlink.core.care.doctorInitials("Dra. Ana Sintética"))
        assertEquals("A", com.luismejias.lumemedlink.core.care.doctorInitials("Ana"))
        assertEquals("?", com.luismejias.lumemedlink.core.care.doctorInitials(" "))
    }
}
