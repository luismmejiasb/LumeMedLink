package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.BookingFailure
import com.luismejias.lumemedlink.core.care.BookingResult
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.ClosedDay
import com.luismejias.lumemedlink.core.care.ClosedKind
import com.luismejias.lumemedlink.core.care.Commune
import com.luismejias.lumemedlink.core.care.DirectoryDoctor
import com.luismejias.lumemedlink.core.care.Slot
import com.luismejias.lumemedlink.core.care.Specialty
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.NO_SECURITY_EVENTS
import com.luismejias.lumemedlink.core.networking.NetworkLogEntry
import com.luismejias.lumemedlink.core.networking.NetworkLogSink
import com.luismejias.lumemedlink.core.networking.lumeHttpClient
import com.luismejias.lumemedlink.shared.CalendarDate
import com.luismejias.lumemedlink.shared.ClockTime
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class Mute : NetworkLogSink {
    override fun log(entry: NetworkLogEntry) = Unit
}

/** A professional of the directory, synthetic (§9), with a bookable place in 13101 and one closed in 13114. */
private const val ENTRY = """{"id":"pro-1","givenName":"Ana","familyName":"Sintética","specialtyCode":"MEDICINA_FAMILIAR",
    "professionalTitle":"Dra.","rating":{"average":4.5,"count":12},
    "locations":[{"id":"loc-1","name":"Sede Norte","communeCode":"13101","regionCode":"13","bookable":true,
                  "timeZone":"America/Santiago"},
                 {"id":"loc-2","name":"Sede Sur","communeCode":"13114","regionCode":"13","bookable":false,
                  "timeZone":"America/Santiago"}]}"""

private const val SPECIALTIES = """[{"code":"MEDICINA_FAMILIAR","name":"Medicina Familiar","profession":"MEDICINE",
    "nidCode":null,"isSuppressed":false},{"code":"ANTIGUA","name":"Antigua","profession":"MEDICINE","nidCode":null,
    "isSuppressed":true},{"code":"CARDIOLOGIA","name":"Cardiología","profession":"MEDICINE","nidCode":null,
    "isSuppressed":false}]"""

private class Directory(private val routes: Map<String, Pair<HttpStatusCode, String>>) {
    val seen = mutableListOf<String>()
    val bodies = mutableListOf<String>()

    fun gateway(): RemoteDirectory {
        val engine = MockEngine { request ->
            val key = request.method.value + " " + request.url.encodedPath
            seen += key + if (request.url.encodedQuery.isEmpty()) "" else "?" + request.url.encodedQuery
            bodies += request.body.toByteArray().decodeToString()
            val (status, body) = routes[key] ?: (HttpStatusCode.NotFound to "")
            val type = if (status.value >= 400) "application/problem+json" else "application/json"
            respond(body, status, headersOf(HttpHeaders.ContentType, type))
        }
        val contract = ContractClient(
            lumeHttpClient("https://api.test.lume", engine, Mute(), securityEvents = NO_SECURITY_EVENTS),
        )
        return RemoteDirectory(contract, SpecialtyNames(contract), PlaceZones(TimeZone.of("America/Santiago")))
    }
}

private val CATALOGS = mapOf(
    "GET /v1/reference/specialties" to (HttpStatusCode.OK to SPECIALTIES),
    "GET /v1/reference/regions" to
        (HttpStatusCode.OK to """[{"code":"13","name":"Metropolitana","abbreviation":"RM"}]"""),
    "GET /v1/reference/communes" to (
        HttpStatusCode.OK to """[{"code":"13101","provinceCode":"131","provinceName":"Santiago","regionCode":"13",
        "name":"Santiago"},{"code":"13114","provinceCode":"131","provinceName":"Santiago","regionCode":"13",
        "name":"Las Condes"}]"""
        ),
    "POST /v1/directory/professionals/search" to (HttpStatusCode.OK to "[$ENTRY]"),
)

class RemoteDirectoryTest {
    @Test
    fun specialtiesAreTheCatalogsInAlphabeticalOrderWithoutTheSuppressed() = runTest {
        val list = assertIs<CareResult.Ok<List<Specialty>>>(Directory(CATALOGS).gateway().specialties()).value
        assertEquals(listOf("Cardiología", "Medicina Familiar"), list.map { it.name })
    }

    @Test
    fun aSpecialtysCommunesAreWhereItHasABookablePlaceNamedByTheCatalog() = runTest {
        val directory = Directory(CATALOGS)
        val communes = assertIs<CareResult.Ok<List<Commune>>>(directory.gateway().communes("MEDICINA_FAMILIAR")).value
        assertEquals(listOf(Commune("13101", "Santiago", "Metropolitana")), communes)
        assertEquals(
            """{"specialtyCode":"MEDICINA_FAMILIAR"}""",
            directory.bodies[
                directory.seen.indexOfFirst {
                    "search" in
                        it
                },
            ],
        )
    }

    @Test
    fun aResultIsOneBookablePlaceInTheCommuneWithTheDoctorsWords() = runTest {
        val results = assertIs<CareResult.Ok<List<DirectoryDoctor>>>(
            Directory(CATALOGS).gateway().search("MEDICINA_FAMILIAR", "13101"),
        ).value
        val one = results.single()
        assertEquals("Dra. Ana Sintética", one.doctor.displayName)
        assertEquals("Medicina Familiar", one.doctor.specialty)
        assertEquals("loc-1", one.locationId)
        assertEquals("Santiago", one.commune)
        assertEquals(4.5 to 12, one.rating to one.ratingCount)
    }

    @Test
    fun freeHoursAreAskedForAsTheClinicsDaysAndReadOnItsWallClock() = runTest {
        val directory = Directory(
            CATALOGS + mapOf(
                "GET /v1/directory/professionals/pro-1/availability" to (
                    HttpStatusCode.OK to
                        """[{"startsAt":"2026-10-20T12:00:00.000Z","endsAt":"2026-10-20T12:30:00.000Z"}]"""
                    ),
            ),
        )
        val slots = assertIs<CareResult.Ok<List<Slot>>>(
            directory.gateway().availability("pro-1", "loc-1", CalendarDate(2026, 10, 20), CalendarDate(2026, 10, 21)),
        ).value
        assertEquals(listOf(Slot(CalendarDate(2026, 10, 20), ClockTime(9, 0), 30)), slots)
        assertEquals(
            "GET /v1/directory/professionals/pro-1/availability" +
                "?from=2026-10-20T03%3A00%3A00Z&locationId=loc-1&to=2026-10-22T03%3A00%3A00Z",
            directory.seen.last(),
        )
    }

    @Test
    fun aClosedPeriodEndingAtMidnightClosesItsDaysAndNotTheNextAndAHolidayWins() = runTest {
        // From the 20th 00:00 to the 22nd 00:00 in Santiago (UTC-3): the 20th and the 21st, never the 22nd.
        val directory = Directory(
            CATALOGS + mapOf(
                "GET /v1/directory/professionals/pro-1/closed-periods" to (
                    HttpStatusCode.OK to
                        """[{"startsAt":"2026-10-20T03:00:00.000Z","endsAt":"2026-10-22T03:00:00.000Z"}]"""
                    ),
                "GET /v1/reference/holidays" to (
                    HttpStatusCode.OK to
                        """[{"date":"2026-10-21","name":"Feriado","irrenunciable":false,"legalBasis":"Ley"}]"""
                    ),
            ),
        )
        val closed = assertIs<CareResult.Ok<List<ClosedDay>>>(
            directory.gateway().closedDays("pro-1", "loc-1", CalendarDate(2026, 10, 19), CalendarDate(2026, 10, 25)),
        ).value
        assertEquals(
            listOf(
                ClosedDay(CalendarDate(2026, 10, 20), ClosedKind.NO_ATTENTION),
                ClosedDay(CalendarDate(2026, 10, 21), ClosedKind.HOLIDAY),
            ),
            closed,
        )
    }

    @Test
    fun aPendingRequestIsAlreadyPendingAndATakenHourIsSlotTaken() = runTest {
        fun refusing(code: String) = Directory(
            CATALOGS + mapOf(
                "POST /v1/patient/appointments" to
                    (HttpStatusCode.Conflict to """{"type":"/problems/$code","status":409}"""),
            ),
        )
        val slot = Slot(CalendarDate(2026, 10, 20), ClockTime(9, 0), 30)
        assertEquals(
            BookingResult.Failed(BookingFailure.AlreadyPending),
            refusing("pending_request_exists").gateway().book("pro-1", "loc-1", slot, "c-1"),
        )
        assertEquals(
            BookingResult.Failed(BookingFailure.SlotTaken),
            refusing("conflict").gateway().book("pro-1", "loc-1", slot, "c-1"),
        )
    }

    @Test
    fun nearbyCommunesHaveNoContractAndAreNotInvented() = runTest {
        assertEquals(
            CareResult.Ok(emptyList()),
            Directory(CATALOGS).gateway().nearbyCommunes("MEDICINA_FAMILIAR", "13101"),
        )
    }

    @Test
    fun aProfessionalByIdIsAtTheirFirstBookablePlaceAndGoneIsNull() = runTest {
        val found = Directory(
            CATALOGS + mapOf("GET /v1/directory/professionals/pro-1" to (HttpStatusCode.OK to ENTRY)),
        ).gateway().doctor("pro-1")
        val doctor = assertIs<CareResult.Ok<DirectoryDoctor?>>(found).value
        assertEquals("loc-1" to "Santiago", doctor?.locationId to doctor?.commune)
        assertEquals(CareResult.Ok(null), Directory(CATALOGS).gateway().doctor("pro-gone"))
    }
}
