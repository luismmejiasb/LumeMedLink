package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.AppointmentsSource
import com.luismejias.lumemedlink.core.care.CareDoctor
import com.luismejias.lumemedlink.core.care.CarePlace
import com.luismejias.lumemedlink.core.care.CarePolicy
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.RatingAspect
import com.luismejias.lumemedlink.core.care.RatingDraft
import com.luismejias.lumemedlink.core.care.RatingPrompt
import com.luismejias.lumemedlink.core.care.SentRating
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class RatingsLogs : NetworkLogSink {
    override fun log(entry: NetworkLogEntry) = Unit
}

private class Visits(private val list: List<PatientAppointment>) : AppointmentsSource {
    override suspend fun appointments() = CareResult.Ok(list)

    override suspend fun policy(): CareResult<CarePolicy> = CareResult.Ok(CarePolicy(24, listOf(24, 2)))
}

// Synthetic (§9).
private fun attended(id: String) = PatientAppointment(
    id = id,
    doctor = CareDoctor("pro-1", "Dra. Ana Sintética", ""),
    date = CalendarDate(2026, 10, 1),
    start = ClockTime(10, 0),
    minutes = 30,
    status = AppointmentStatus.COMPLETED,
    place = CarePlace("", ""),
)

private const val RATEABLE = """{"items":[{"appointmentId":"a-1","clinicId":"t-1","endsAt":"2026-10-01T13:30:00.000Z",
    "rateableUntil":"2026-10-31T13:30:00.000Z"},{"appointmentId":"a-unknown","clinicId":"t-1",
    "endsAt":"2026-10-02T13:30:00.000Z","rateableUntil":"2026-11-01T13:30:00.000Z"}]}"""

private const val RATINGS = """{"items":[{"id":"r-1","appointmentId":"a-0","clinicId":"t-1","stars":4,"aspects":[],
    "createdAt":"2026-09-20T15:00:00.000Z"}]}"""

private const val RATED = """{"id":"r-2","appointmentId":"a-1","clinicId":"t-1","stars":5,"aspects":["KIND"],
    "createdAt":"2026-10-15T15:00:00.000Z"}"""

/** The platform's ratings, scripted: what each route answers, and what each write carried. */
private class Platform(var rateStatus: HttpStatusCode = HttpStatusCode.Created) {
    val sent = mutableListOf<String>()

    fun ratings(visits: List<PatientAppointment> = listOf(attended("a-1"))): RemoteRatings {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                path.endsWith("/rateable-attentions") -> respond(RATEABLE, HttpStatusCode.OK, json)
                path.endsWith("/v1/patient/ratings") -> respond(RATINGS, HttpStatusCode.OK, json)
                path.endsWith("/rating") -> {
                    sent += request.body.toByteArray().decodeToString()
                    if (rateStatus == HttpStatusCode.Created) {
                        respond(RATED, rateStatus, json)
                    } else {
                        respond("", rateStatus)
                    }
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val contract = ContractClient(
            lumeHttpClient("https://api.test.lume", engine, RatingsLogs(), securityEvents = NO_SECURITY_EVENTS),
        )
        return RemoteRatings(contract, Visits(visits))
    }
}

/** Ratings over the contract (the platform's `ADR-0095`): stars and closed aspects, never free text, with consent. */
class RemoteRatingsTest {
    @Test
    fun aRateableAttentionIsJoinedWithItsAppointmentAndOneThatIsNotOursIsLeftOut() = runTest {
        val prompts = assertIs<CareResult.Ok<List<RatingPrompt>>>(Platform().ratings().ratingPrompts()).value
        assertEquals(listOf("a-1"), prompts.map { it.appointmentId })
        assertEquals("pro-1" to CalendarDate(2026, 10, 1), prompts.single().let { it.doctor.id to it.date })
    }

    @Test
    fun theRatingsGivenAreCountedAndAVisitsOwnIsFoundByItsAppointment() = runTest {
        val ratings = Platform().ratings()
        assertEquals(CareResult.Ok(1), ratings.sentRatingCount())
        assertEquals(CareResult.Ok(SentRating(4, CalendarDate(2026, 9, 20))), ratings.ratingFor("a-0"))
        assertNull(assertIs<CareResult.Ok<SentRating?>>(ratings.ratingFor("a-1")).value, "not rated yet")
    }

    @Test
    fun aRatingCarriesStarsTheContractsAspectsAndTheConsentWithItsVersion() = runTest {
        val platform = Platform()
        val draft = RatingDraft(5, setOf(RatingAspect.KIND, RatingAspect.SOLVED_IT))
        assertEquals(CareResult.Ok(Unit), platform.ratings().rate("a-1", draft))
        val body = Json.parseToJsonElement(platform.sent.single()).jsonObject
        assertEquals(5, body.getValue("stars").jsonPrimitive.content.toInt())
        assertEquals(
            setOf("KIND", "SOLVED_IT"),
            body.getValue("aspects").jsonArray.map { it.jsonPrimitive.content }.toSet(),
        )
        assertTrue(body.getValue("consent").jsonPrimitive.boolean)
        assertEquals(RATING_CONSENT_VERSION, body.getValue("consentVersion").jsonPrimitive.content)
        assertEquals(setOf("stars", "aspects", "clientId", "consent", "consentVersion"), body.keys, "no free text")
    }

    @Test
    fun aRetryAfterAFailureCarriesTheSameKey() = runTest {
        val platform = Platform(rateStatus = HttpStatusCode.InternalServerError)
        val ratings = platform.ratings()
        val draft = RatingDraft(3, emptySet())
        assertIs<CareResult.Failed>(ratings.rate("a-1", draft))
        platform.rateStatus = HttpStatusCode.Created
        assertEquals(CareResult.Ok(Unit), ratings.rate("a-1", draft))
        val keys = platform.sent.map {
            Json.parseToJsonElement(it).jsonObject.getValue("clientId").jsonPrimitive.content
        }
        assertEquals(1, keys.toSet().size, "the platform answers a retried key with the rating it wrote")
    }
}
