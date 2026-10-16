package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.CareDoctor
import com.luismejias.lumemedlink.core.care.CareFailure
import com.luismejias.lumemedlink.core.care.CarePlace
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.Commune
import com.luismejias.lumemedlink.core.care.DirectoryDoctor
import com.luismejias.lumemedlink.core.care.DirectorySearch
import com.luismejias.lumemedlink.core.care.NearbyCommune
import com.luismejias.lumemedlink.core.care.OverbookDraft
import com.luismejias.lumemedlink.core.care.OverbookFailure
import com.luismejias.lumemedlink.core.care.OverbookReason
import com.luismejias.lumemedlink.core.care.OverbookRequest
import com.luismejias.lumemedlink.core.care.OverbookRequestStatus
import com.luismejias.lumemedlink.core.care.OverbookResult
import com.luismejias.lumemedlink.core.care.OverbookUrgency
import com.luismejias.lumemedlink.core.care.Specialty
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.NO_SECURITY_EVENTS
import com.luismejias.lumemedlink.core.networking.NetworkLogEntry
import com.luismejias.lumemedlink.core.networking.NetworkLogSink
import com.luismejias.lumemedlink.core.networking.lumeHttpClient
import com.luismejias.lumemedlink.shared.CalendarDate
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class OverbookQuietLog : NetworkLogSink {
    override fun log(entry: NetworkLogEntry) = Unit
}

/** The patient's profile at one clinic, synthetic (§9). */
private const val OVERBOOK_PROFILES = """[{"tenantId":"t-1","tenantName":"Centro Sintético","givenName":"Ana","familyName":"Soto",
    "email":null,"phone":null,"street":null,"streetNumber":null,"unit":null,"communeCode":null,"regionCode":null,
    "postalCode":null,"latitude":null,"longitude":null,"practiceContact":null,"rut":"18456789-K"}]"""

private fun overbookJson(id: String, professional: String, clinic: String, status: String, origin: String) =
    """{"id":"$id","professionalProfileId":"$professional","origin":"$origin","urgency":"NORMAL",""" +
        """"reason":"EARLIER_FOLLOW_UP","status":"$status","createdAt":"2026-10-15T02:30:00.000Z",""" +
        """"resolvedAt":null,"appointmentId":null,"clinicId":"$clinic"}"""

private val OVERBOOK_LIST = """{"items":[""" +
    overbookJson("o-1", "p-known", "t-1", "PENDING", "PATIENT_APP") + "," +
    overbookJson("o-2", "p-gone", "t-9", "SOMETHING_NEW", "PHONE") + "]}"

private val OVERBOOK_DOCTOR = CareDoctor("p-known", "Dra. Ana Sintética", "Medicina Familiar")

/** The directory, answering one professional by id; [failing] makes it unreadable. */
private class OneDoctorDirectory(private val failing: Boolean = false) : DirectorySearch {
    val asked = mutableListOf<String>()

    override suspend fun doctor(doctorId: String): CareResult<DirectoryDoctor?> {
        asked += doctorId
        if (failing) return CareResult.Failed(CareFailure.Unavailable)
        return CareResult.Ok(
            if (doctorId == OVERBOOK_DOCTOR.id) {
                DirectoryDoctor(OVERBOOK_DOCTOR, "l-1", "Providencia", CarePlace("", ""), null, 0, 0, null)
            } else {
                null
            },
        )
    }

    override suspend fun specialties(): CareResult<List<Specialty>> = CareResult.Ok(emptyList())

    override suspend fun communes(): CareResult<List<Commune>> = CareResult.Ok(emptyList())

    override suspend fun search(specialtyCode: String, communeCode: String): CareResult<List<DirectoryDoctor>> =
        CareResult.Ok(emptyList())

    override suspend fun nearbyCommunes(specialtyCode: String, communeCode: String): CareResult<List<NearbyCommune>> =
        CareResult.Ok(emptyList())
}

/** The platform's answers by route; every request it saw, with its body. */
private class OverbookPlatform(
    private val list: Pair<HttpStatusCode, String> = HttpStatusCode.OK to OVERBOOK_LIST,
    private val profiles: Pair<HttpStatusCode, String> = HttpStatusCode.OK to OVERBOOK_PROFILES,
    private val write: Pair<HttpStatusCode, String> =
        HttpStatusCode.Created to overbookJson("o-3", "p-known", "t-1", "PENDING", "PATIENT_APP"),
    val directory: OneDoctorDirectory = OneDoctorDirectory(),
) {
    val seen = mutableListOf<Triple<HttpMethod, String, String?>>()

    fun gateway(): RemoteOverbooks {
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            seen += Triple(request.method, path, (request.body as? TextContent)?.text)
            val (status, body) = when {
                path.endsWith("/profiles") -> profiles
                request.method == HttpMethod.Get -> list
                else -> write
            }
            val type = if (status.value >= 400) "application/problem+json" else "application/json"
            respond(body, status, headersOf(HttpHeaders.ContentType, type))
        }
        val contract = ContractClient(
            lumeHttpClient("https://api.test.lume", engine, OverbookQuietLog(), securityEvents = NO_SECURITY_EVENTS),
        )
        return RemoteOverbooks(contract, directory)
    }
}

private fun overbookProblem(code: String) = """{"type":"/problems/$code","status":409}"""

private fun waitingRequest(doctor: CareDoctor, clinic: String, byPhone: Boolean) =
    OverbookRequest("o-1", doctor, clinic, OverbookRequestStatus.PENDING, CalendarDate(2026, 10, 14), byPhone)

private val OVERBOOK_DRAFT = OverbookDraft("p-known", OverbookUrgency.PRIORITY, OverbookReason.RENEW_PRESCRIPTION)

class RemoteOverbooksTest {
    @Test
    fun theListNamesTheProfessionalAndTheClinicAndAnUnknownStatusIsNoError() = runTest {
        val list = assertIs<CareResult.Ok<List<OverbookRequest>>>(OverbookPlatform().gateway().overbookRequests()).value
        assertEquals(listOf("o-1", "o-2"), list.map { it.id }, "the platform's order, newest first")
        val (mine, recorded) = list
        assertEquals(OVERBOOK_DOCTOR, mine.doctor)
        assertEquals("Centro Sintético", mine.clinic)
        assertEquals(OverbookRequestStatus.PENDING, mine.status)
        assertTrue(mine.waiting)
        assertFalse(mine.byPhone)
        assertEquals(CalendarDate(2026, 10, 14), mine.askedOn, "02:30 UTC is the evening before on the clinics' clock")
        assertNull(recorded.doctor, "a professional the directory no longer lists is left unnamed")
        assertEquals("", recorded.clinic, "a clinic of no profile: unnamed")
        assertEquals(OverbookRequestStatus.OTHER, recorded.status)
        assertTrue(recorded.byPhone)
    }

    @Test
    fun eachProfessionalIsAskedOnce() = runTest {
        val platform = OverbookPlatform(
            list = HttpStatusCode.OK to """{"items":[""" +
                overbookJson("o-1", "p-known", "t-1", "PENDING", "PATIENT_APP") + "," +
                overbookJson("o-2", "p-known", "t-1", "DECLINED", "PHONE") + "]}",
        )
        platform.gateway().overbookRequests()
        assertEquals(listOf("p-known"), platform.directory.asked)
    }

    @Test
    fun theNamesOnlyLabelTheListAndTheirFailureIsNeverTheLists() = runTest {
        val platform = OverbookPlatform(
            profiles = HttpStatusCode.ServiceUnavailable to overbookProblem("unavailable"),
            directory = OneDoctorDirectory(failing = true),
        )
        val list = assertIs<CareResult.Ok<List<OverbookRequest>>>(platform.gateway().overbookRequests()).value
        assertEquals(listOf(null, null), list.map { it.doctor })
        assertEquals(listOf("", ""), list.map { it.clinic })
    }

    @Test
    fun anUnreadableListIsTheScreensFailure() = runTest {
        val platform = OverbookPlatform(list = HttpStatusCode.ServiceUnavailable to overbookProblem("unavailable"))
        assertEquals(CareResult.Failed(CareFailure.Unavailable), platform.gateway().overbookRequests())
    }

    @Test
    fun askingSendsTheProfessionalTheClosedValuesAndTheAttemptsKey() = runTest {
        val platform = OverbookPlatform()
        val done = assertIs<OverbookResult.Done>(platform.gateway().requestOverbook(OVERBOOK_DRAFT, "key-1"))
        assertEquals("o-3", done.request.id)
        assertTrue(done.request.waiting)
        val (method, path, body) = platform.seen.single()
        assertEquals(HttpMethod.Post, method)
        assertTrue(path.endsWith("/v1/patient/overbook-requests"), path)
        val sent = requireNotNull(body)
        listOf(
            "\"professionalId\":\"p-known\"",
            "\"urgency\":\"PRIORITY\"",
            "\"reason\":\"RENEW_PRESCRIPTION\"",
            "\"clientId\":\"key-1\"",
        ).forEach { assertTrue(it in sent, it) }
    }

    @Test
    fun withdrawingNamesTheRequestByItsId() = runTest {
        val withdrawn = overbookJson("o-1", "p-known", "t-1", "WITHDRAWN", "PHONE")
        val platform = OverbookPlatform(write = HttpStatusCode.OK to withdrawn)
        val request = waitingRequest(OVERBOOK_DOCTOR, clinic = "", byPhone = false)
        val done = assertIs<OverbookResult.Done>(platform.gateway().withdrawOverbookRequest(request))
        assertEquals(OverbookRequestStatus.WITHDRAWN, done.request.status)
        val (method, path) = platform.seen.single()
        assertEquals(HttpMethod.Post, method)
        assertTrue(path.endsWith("/v1/patient/overbook-requests/o-1/withdraw"), path)
    }

    @Test
    fun eachRefusalOfTheContractHasItsOwnName() = runTest {
        val cases = mapOf(
            (HttpStatusCode.NotFound to overbookProblem("not_found")) to OverbookFailure.NotTaken,
            (HttpStatusCode.Conflict to overbookProblem("pending_request_exists")) to OverbookFailure.AlreadyWaiting,
            (HttpStatusCode.Conflict to overbookProblem("conflict")) to OverbookFailure.NoLongerWaiting,
            (HttpStatusCode.ServiceUnavailable to overbookProblem("unavailable")) to OverbookFailure.Unavailable,
        )
        cases.forEach { (answer, failure) ->
            assertEquals(
                OverbookResult.Failed(failure),
                OverbookPlatform(write = answer).gateway().requestOverbook(OVERBOOK_DRAFT, "key-1"),
                answer.second,
            )
        }
    }

    @Test
    fun theReasonAndTheUrgencyNeverReachAPrintedForm() {
        val printed = OVERBOOK_DRAFT.toString() +
            waitingRequest(OVERBOOK_DOCTOR, clinic = "Centro", byPhone = true)
        listOf("RENEW", "PRIORITY", "p-known", "Centro", "Sintética").forEach { assertFalse(it in printed, it) }
    }
}
