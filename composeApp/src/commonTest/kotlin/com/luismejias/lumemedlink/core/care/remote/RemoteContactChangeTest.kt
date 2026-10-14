package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.ContactChangeFailure
import com.luismejias.lumemedlink.core.care.ContactChangeResult
import com.luismejias.lumemedlink.core.care.ContactCodeDispatch
import com.luismejias.lumemedlink.core.care.PendingContactChange
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.NO_SECURITY_EVENTS
import com.luismejias.lumemedlink.core.networking.NetworkLogEntry
import com.luismejias.lumemedlink.core.networking.NetworkLogSink
import com.luismejias.lumemedlink.core.networking.lumeHttpClient
import com.luismejias.lumemedlink.shared.EmailAddress
import com.luismejias.lumemedlink.shared.PhoneNumber
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

private class Hush : NetworkLogSink {
    override fun log(entry: NetworkLogEntry) = Unit
}

/** What the platform opens a change with (its answer `0077`). Synthetic (§9). */
private const val OPENED = """{"id":"v-1","channel":"PHONE","expiresAt":"2026-10-14T21:10:00.000Z",
    "resendAvailableAt":"2026-10-14T21:01:00.000Z","sendsLeft":2,"attemptsLeft":5,"ticket":"t-secret"}"""

private const val RESENT = """{"id":"v-1","channel":"PHONE","expiresAt":"2026-10-14T21:20:00.000Z",
    "resendAvailableAt":"2026-10-14T21:11:00.000Z","sendsLeft":1,"attemptsLeft":5}"""

private fun problem(code: String, extra: String = "") = """{"type":"/problems/$code","status":0$extra}"""

private class Verifications(private val answers: List<Pair<HttpStatusCode, String>>) {
    val sent = mutableListOf<Pair<String, String>>()
    var signIns = 0

    fun gateway(): RemoteContactChange {
        val engine = MockEngine { request ->
            sent +=
                (request.method.value + " " + request.url.encodedPath) to request.body.toByteArray().decodeToString()
            val (status, body) = answers[minOf(sent.size, answers.size) - 1]
            val type = if (status.value >= 400) "application/problem+json" else "application/json"
            respond(body, status, headersOf(HttpHeaders.ContentType, type))
        }
        val contract = ContractClient(
            lumeHttpClient("https://api.test.lume", engine, Hush(), securityEvents = NO_SECURITY_EVENTS),
        )
        return RemoteContactChange(contract) { signIns += 1 }
    }
}

private val CHANGE = PendingContactChange("v-1", "t-secret", "+56912345678")

class RemoteContactChangeTest {
    @Test
    fun askingSignsInAfreshThenSendsTheNewContactAndKeepsTheTicketForTheNextSteps() = runTest {
        val platform = Verifications(listOf(HttpStatusCode.Created to OPENED))
        val gateway = platform.gateway()

        val dispatch = assertIs<ContactChangeResult.Ok<ContactCodeDispatch>>(
            gateway.requestPhoneChange(requireNotNull(PhoneNumber.chileanMobile("+56912345678"))),
        ).value

        assertEquals(1, platform.signIns)
        assertEquals(
            "POST /v1/patient/contact-verifications" to """{"channel":"PHONE","value":"+56912345678"}""",
            platform.sent.single(),
        )
        assertEquals(
            1_792_012_200_000L to 1_792_011_660_000L,
            dispatch.expiresAtEpochMillis to dispatch.resendAvailableAtEpochMillis,
        )
        assertEquals(
            Triple("v-1", "t-secret", "+56912345678"),
            Triple(dispatch.change.id, dispatch.change.ticket, dispatch.change.value),
        )
        assertFalse("t-secret" in dispatch.toString(), "the ticket never prints")
    }

    @Test
    fun anEmailIsAskedForOnItsChannel() = runTest {
        val platform = Verifications(listOf(HttpStatusCode.Created to OPENED))
        platform.gateway().requestEmailChange(requireNotNull(EmailAddress.parse("nueva@correo.cl")))
        assertEquals("""{"channel":"EMAIL","value":"nueva@correo.cl"}""", platform.sent.single().second)
    }

    @Test
    fun aResendAndAConfirmationNameTheChangeWithItsTicketAndValue() = runTest {
        val platform = Verifications(listOf(HttpStatusCode.OK to RESENT, HttpStatusCode.OK to "[]"))
        val gateway = platform.gateway()

        val resent = assertIs<ContactChangeResult.Ok<ContactCodeDispatch>>(gateway.resendContactCode(CHANGE)).value
        assertEquals(ContactChangeResult.Ok(Unit), gateway.confirmContactChange(CHANGE, "123456"))

        assertEquals(CHANGE, resent.change)
        assertEquals(
            listOf(
                "POST /v1/patient/contact-verifications/v-1/resend" to
                    """{"ticket":"t-secret","value":"+56912345678"}""",
                "POST /v1/patient/contact-verifications/v-1/confirm" to
                    """{"ticket":"t-secret","value":"+56912345678","code":"123456"}""",
            ),
            platform.sent,
        )
        assertEquals(0, platform.signIns, "only asking needs the recent sign-in")
    }

    @Test
    fun aWrongCodeSaysTheTriesLeftAndTheLastOneClosesTheChange() = runTest {
        fun wrong(left: Int) = Verifications(
            listOf(
                HttpStatusCode.UnprocessableEntity to
                    problem("contact_verification_code_mismatch", ""","attemptsLeft":$left"""),
            ),
        )
        assertEquals(
            ContactChangeResult.Failed(ContactChangeFailure.WrongCode(3)),
            wrong(3).gateway().confirmContactChange(CHANGE, "000000"),
        )
        assertEquals(
            ContactChangeResult.Failed(ContactChangeFailure.Closed),
            wrong(0).gateway().confirmContactChange(CHANGE, "000000"),
        )
    }

    @Test
    fun aContactChangedMeanwhileSendsTheSameCodeOnceMore() = runTest {
        val once = Verifications(listOf(HttpStatusCode.Conflict to problem("stale_write"), HttpStatusCode.OK to "[]"))
        assertEquals(ContactChangeResult.Ok(Unit), once.gateway().confirmContactChange(CHANGE, "123456"))
        assertEquals(2, once.sent.size)

        val twice = Verifications(listOf(HttpStatusCode.Conflict to problem("stale_write")))
        assertEquals(
            ContactChangeResult.Failed(ContactChangeFailure.Unavailable),
            twice.gateway().confirmContactChange(CHANGE, "123456"),
        )
        assertEquals(2, twice.sent.size, "once more, never a loop")
    }

    @Test
    fun eachRefusalOfThePlatformIsTheSheetsWord() = runTest {
        val cases = listOf(
            (HttpStatusCode.Forbidden to problem("recent_sign_in_required")) to ContactChangeFailure.SignInAgain,
            (HttpStatusCode.Conflict to problem("contact_unchanged")) to ContactChangeFailure.Unchanged,
            (HttpStatusCode.Gone to problem("contact_verification_expired")) to ContactChangeFailure.CodeExpired,
            (HttpStatusCode.Conflict to problem("contact_verification_closed")) to ContactChangeFailure.Closed,
            (HttpStatusCode.TooManyRequests to problem("contact_verification_limit_reached")) to
                ContactChangeFailure.LimitReached,
            (HttpStatusCode.NotFound to problem("not_found")) to ContactChangeFailure.Closed,
            (HttpStatusCode.ServiceUnavailable to problem("service_unavailable")) to ContactChangeFailure.Unavailable,
        )
        cases.forEach { (answer, failure) ->
            assertEquals(
                ContactChangeResult.Failed(failure),
                Verifications(listOf(answer)).gateway().requestPhoneChange(
                    requireNotNull(PhoneNumber.chileanMobile("+56912345678")),
                ),
                answer.second,
            )
        }
    }
}
