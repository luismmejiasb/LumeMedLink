package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.CareFailure
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.PatientProfile
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.NO_SECURITY_EVENTS
import com.luismejias.lumemedlink.core.networking.NetworkLogEntry
import com.luismejias.lumemedlink.core.networking.NetworkLogSink
import com.luismejias.lumemedlink.core.networking.lumeHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

private class Quiet : NetworkLogSink {
    override fun log(entry: NetworkLogEntry) = Unit
}

/** One clinic's profile as the contract writes it, every required field present. Synthetic (§9). */
private fun profileJson(
    tenantId: String,
    tenantName: String,
    phone: String? = "+56987654321",
    email: String? = "ana.sintetica@correo.cl",
    rut: String = "18456789-K",
): String {
    fun q(value: String?) = value?.let { "\"$it\"" } ?: "null"
    return """{"tenantId":"$tenantId","tenantName":"$tenantName","givenName":"Ana","familyName":"Sintética",
        "email":${q(email)},"phone":${q(phone)},"street":null,"streetNumber":null,"unit":null,"communeCode":null,
        "regionCode":null,"postalCode":null,"latitude":null,"longitude":null,"practiceContact":null,"rut":"$rut"}"""
}

private class Profiles(private val status: HttpStatusCode, private val body: String) {
    val seen = mutableListOf<String>()

    fun source(): RemoteProfile {
        val engine = MockEngine { request ->
            seen += request.method.value + " " + request.url.encodedPath
            val type = if (status.value >= 400) "application/problem+json" else "application/json"
            respond(body, status, headersOf(HttpHeaders.ContentType, type))
        }
        return RemoteProfile(
            ContractClient(
                lumeHttpClient("https://api.test.lume", engine, Quiet(), securityEvents = NO_SECURITY_EVENTS),
            ),
        )
    }
}

class RemoteProfileTest {
    @Test
    fun theProfileShownIsTheClinicFirstByNameWhateverOrderTheServerSends() = runTest {
        val profiles = Profiles(
            HttpStatusCode.OK,
            "[" + profileJson("t-2", "Clínica Sur", phone = "+56911112222") + "," +
                profileJson("t-1", "Centro Norte", phone = "+56933334444") + "]",
        )
        val shown = assertIs<CareResult.Ok<PatientProfile>>(profiles.source().profile()).value
        assertEquals("+56933334444", shown.mobile?.e164)
        assertEquals(listOf("GET /v1/patient/profiles"), profiles.seen)
    }

    @Test
    fun theNamesTheRutAndTheContactsAreTheClinicsAndNothingIsClaimedVerified() = runTest {
        val shown = assertIs<CareResult.Ok<PatientProfile>>(
            Profiles(HttpStatusCode.OK, "[" + profileJson("t-1", "Centro") + "]").source().profile(),
        ).value
        assertEquals("Ana" to "Sintética", shown.givenNames to shown.familyNames)
        assertEquals("AS", shown.initials)
        assertEquals("18.456.789-K", shown.rut?.display)
        assertEquals("ana.sintetica@correo.cl", shown.email?.value)
        assertFalse(shown.verified, "the contract states no verification")
    }

    @Test
    fun aContactTheClinicDoesNotHoldIsNotRecordedNeverInvented() = runTest {
        val shown = assertIs<CareResult.Ok<PatientProfile>>(
            Profiles(
                HttpStatusCode.OK,
                "[" + profileJson("t-1", "Centro", phone = null, email = "not-an-address", rut = "18456789-5") + "]",
            ).source().profile(),
        ).value
        assertNull(shown.mobile)
        assertNull(shown.email, "a value in no shape is not shown as an address")
        assertNull(shown.rut, "a RUT whose check digit fails")
    }

    @Test
    fun noProfileAtAllAndAFailedReadAreUnavailable() = runTest {
        assertEquals(CareResult.Failed(CareFailure.Unavailable), Profiles(HttpStatusCode.OK, "[]").source().profile())
        assertEquals(
            CareResult.Failed(CareFailure.Unavailable),
            Profiles(HttpStatusCode.InternalServerError, "").source().profile(),
        )
    }
}
