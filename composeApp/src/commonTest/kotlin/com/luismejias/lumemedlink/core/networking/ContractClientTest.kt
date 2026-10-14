package com.luismejias.lumemedlink.core.networking

import com.luismejias.lumemedlink.core.networking.contract.AvailabilitySlotDto
import com.luismejias.lumemedlink.core.networking.contract.CancelMyAppointmentDto
import com.luismejias.lumemedlink.core.networking.contract.CancelMyAppointmentDtoCancellationReason
import com.luismejias.lumemedlink.core.networking.contract.ContractOperations
import com.luismejias.lumemedlink.core.networking.contract.HolidayDto
import com.luismejias.lumemedlink.core.networking.contract.ReportSecurityEventDtoKind
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val BASE = "https://api.test.lume"
private val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")

private class NoLog : NetworkLogSink {
    override fun log(entry: NetworkLogEntry) = Unit
}

/** What the server saw, for the assertions. */
private class Seen {
    var method: HttpMethod? = null
    var url: String? = null
    var body: String? = null
}

private fun clientAnswering(seen: Seen, status: HttpStatusCode, body: String): ContractClient {
    val engine = MockEngine { request ->
        seen.method = request.method
        seen.url = request.url.toString()
        seen.body = request.body.toByteArray().decodeToString()
        respond(body, status, JSON_HEADERS)
    }
    return ContractClient(lumeHttpClient(BASE, engine, NoLog(), securityEvents = NO_SECURITY_EVENTS))
}

class ContractClientTest {
    @Test
    fun aPathParameterIsFilledEncodedAndTheRouteStaysRelative() {
        val url = contractUrl(ContractOperations.CANCEL_MY_APPOINTMENT, mapOf("id" to "a/b c"), emptyMap())
        assertEquals("v1/patient/appointments/a%2Fb%20c/cancel", url)
    }

    @Test
    fun theQueryIsEncodedInAStableOrder() {
        val url = contractUrl(
            ContractOperations.LIST_HOLIDAYS,
            emptyMap(),
            mapOf("to" to "2026-10-31", "from" to "2026-10-01"),
        )
        assertEquals("v1/reference/holidays?from=2026-10-01&to=2026-10-31", url)
    }

    @Test
    fun aMissingOrExtraPathParameterIsRefusedBeforeAnythingIsSent() {
        assertFailsWith<IllegalArgumentException> {
            contractUrl(ContractOperations.CANCEL_MY_APPOINTMENT, emptyMap(), emptyMap())
        }
        assertFailsWith<IllegalArgumentException> {
            contractUrl(ContractOperations.CANCEL_MY_APPOINTMENT, mapOf("id" to "1", "other" to "2"), emptyMap())
        }
    }

    @Test
    fun aPathValueThatWouldLeaveItsSegmentIsRefused() {
        listOf("", ".", "..").forEach { value ->
            assertFailsWith<IllegalArgumentException>("'$value'") {
                contractUrl(ContractOperations.CANCEL_MY_APPOINTMENT, mapOf("id" to value), emptyMap())
            }
        }
    }

    @Test
    fun aMissingRequiredOrAnUnknownQueryParameterIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            contractUrl(ContractOperations.LIST_HOLIDAYS, emptyMap(), mapOf("from" to "2026-10-01"))
        }
        assertFailsWith<IllegalArgumentException> {
            contractUrl(
                ContractOperations.LIST_HOLIDAYS,
                emptyMap(),
                mapOf("from" to "2026-10-01", "to" to "2026-10-31", "rut" to "x"),
            )
        }
    }

    @Test
    fun theRefusalNamesTheParameterNeverItsValue() {
        val refusal = assertFailsWith<IllegalArgumentException> {
            contractUrl(ContractOperations.CANCEL_MY_APPOINTMENT, mapOf("id" to ".."), emptyMap())
        }
        assertTrue(refusal.message.orEmpty().contains("'id'"))
        assertFalse(refusal.message.orEmpty().contains(".."), "the value leaked into the message")
    }

    @Test
    fun aGetDecodesTheAnswerAndIgnoresAFieldTheServerAddedSince() = runTest {
        val seen = Seen()
        val client = clientAnswering(
            seen,
            HttpStatusCode.OK,
            """[{"startsAt":"2026-10-15T12:00:00.000Z","endsAt":"2026-10-15T12:30:00.000Z","addedLater":true}]""",
        )

        val slots = client.call(
            ContractOperations.GET_DIRECTORY_AVAILABILITY,
            path = mapOf("professionalId" to "p-1"),
            query = mapOf("locationId" to "l-1", "from" to "2026-10-15", "to" to "2026-10-15"),
        )

        assertEquals(listOf(AvailabilitySlotDto("2026-10-15T12:00:00.000Z", "2026-10-15T12:30:00.000Z")), slots)
        assertEquals(HttpMethod.Get, seen.method)
        assertEquals(
            "$BASE/v1/directory/professionals/p-1/availability?from=2026-10-15&locationId=l-1&to=2026-10-15",
            seen.url,
        )
    }

    @Test
    fun aPostSendsTheBodyInTheContractsWordsAndANoContentAnswerIsUnit() = runTest {
        val seen = Seen()
        val client = clientAnswering(seen, HttpStatusCode.NoContent, "")

        client.call(
            ContractOperations.CANCEL_MY_APPOINTMENT,
            CancelMyAppointmentDto(CancelMyAppointmentDtoCancellationReason.SCHEDULE_CONFLICT),
            path = mapOf("id" to "appt-1"),
        )

        assertEquals(HttpMethod.Post, seen.method)
        assertEquals("""{"cancellationReason":"SCHEDULE_CONFLICT"}""", seen.body)
    }

    @Test
    fun aRefusalFromTheServerArrivesAsTheStacksError() = runTest {
        val client = clientAnswering(Seen(), HttpStatusCode.NotFound, "")
        assertFailsWith<AppErrorException> {
            client.call(ContractOperations.LIST_HOLIDAYS, query = mapOf("from" to "2026-10-01", "to" to "2026-10-31"))
        }
    }

    @Test
    fun aValueOfTheVocabularyTheAppDoesNotKnowDecodesAndStaysAsSent() {
        val kind = Json.decodeFromString(ReportSecurityEventDtoKind.serializer(), "\"somethingNew\"")
        assertEquals("somethingNew", kind.wire)
    }

    @Test
    fun aGeneratedModelPrintsItsNameAndNoneOfItsValues() {
        val holiday = HolidayDto("2026-09-18", "Fiestas Patrias", irrenunciable = true, legalBasis = "Ley")
        assertEquals("HolidayDto", holiday.toString())
    }
}
