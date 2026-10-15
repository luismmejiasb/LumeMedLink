package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.CareFailure
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.NoticeMark
import com.luismejias.lumemedlink.core.care.PatientNotice
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.NO_SECURITY_EVENTS
import com.luismejias.lumemedlink.core.networking.NetworkLogEntry
import com.luismejias.lumemedlink.core.networking.NetworkLogSink
import com.luismejias.lumemedlink.core.networking.lumeHttpClient
import com.luismejias.lumemedlink.core.session.SecureStoreKey
import com.luismejias.lumemedlink.core.telemetry.MemorySecureStore
import com.luismejias.lumemedlink.shared.CalendarDate
import com.luismejias.lumemedlink.shared.ClockTime
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
import kotlin.test.assertTrue

private class Mum : NetworkLogSink {
    override fun log(entry: NetworkLogEntry) = Unit
}

/** A notice as the platform's inbox writes it. Synthetic (§9), and no clinical word in it (§1.0). */
private const val PROFILES = """[{"tenantId":"t-1","tenantName":"Centro Sintético","givenName":"Ana","familyName":"Soto",
    "email":null,"phone":null,"street":null,"streetNumber":null,"unit":null,"communeCode":null,"regionCode":null,
    "postalCode":null,"latitude":null,"longitude":null,"practiceContact":null,"rut":"18456789-K"}]"""

private class Inbox(private val notices: String, private val status: HttpStatusCode = HttpStatusCode.OK) {
    val store = MemorySecureStore()

    fun source(): RemoteNotices {
        val engine = MockEngine { request ->
            val body = if (request.url.encodedPath.endsWith("/profiles")) PROFILES else notices
            val type = if (status.value >= 400) "application/problem+json" else "application/json"
            respond(body, status, headersOf(HttpHeaders.ContentType, type))
        }
        val contract = ContractClient(
            lumeHttpClient("https://api.test.lume", engine, Mum(), securityEvents = NO_SECURITY_EVENTS),
        )
        return RemoteNotices(contract, NoticeMark(store))
    }
}

private const val TWO = """{"items":[""" +
    """{"id":"n-1","clinicId":"t-1","kind":"APPOINTMENT_REMINDER","title":"Antes","text":null,""" +
    """"createdAt":"2026-10-13T12:00:00.000Z"},""" +
    """{"id":"n-2","clinicId":"t-9","kind":"SOMETHING_NEW","title":"Después","text":"Hola",""" +
    """"createdAt":"2026-10-14T13:30:00.000Z"}],"nextCursor":null}"""

class RemoteNoticesTest {
    @Test
    fun theListIsNewestFirstOnTheClinicsClockNamedByTheProfileAndAnUnknownKindIsNoError() = runTest {
        val list = assertIs<CareResult.Ok<List<PatientNotice>>>(Inbox(TWO).source().notices()).value
        assertEquals(listOf("n-2", "n-1"), list.map { it.id })
        val newest = list.first()
        assertEquals(CalendarDate(2026, 10, 14) to ClockTime(10, 30), newest.sentOn to newest.sentAt, "UTC-3")
        assertEquals("" to "Centro Sintético", newest.clinic to list.last().clinic, "a clinic of no profile: unnamed")
        assertEquals(null, list.last().text, "the title stands alone")
    }

    @Test
    fun theBellIsNewUntilTheNewestNoticeIsShownAndAnOlderMarkNeverMovesItBack() = runTest {
        val inbox = Inbox(TWO)
        val source = inbox.source()
        assertEquals(CareResult.Ok(2), source.unreadNoticeCount())

        val newest = assertIs<CareResult.Ok<List<PatientNotice>>>(source.notices()).value.first().sentAtEpochMillis
        source.noticesSeen(newest)
        assertEquals(CareResult.Ok(0), source.unreadNoticeCount())

        source.noticesSeen(newest - 1)
        assertEquals(newest.toString(), inbox.store.entries[SecureStoreKey.NOTICES_SEEN_UNTIL.storageKey])
    }

    @Test
    fun theCountIsWhatWasSentAfterTheMark() = runTest {
        val inbox = Inbox(TWO)
        val source = inbox.source()
        val older = assertIs<CareResult.Ok<List<PatientNotice>>>(source.notices()).value.last().sentAtEpochMillis
        source.noticesSeen(older)
        assertEquals(CareResult.Ok(1), source.unreadNoticeCount())
    }

    @Test
    fun anEmptyInboxRingsNothingAndAFailedReadFailsTheBell() = runTest {
        assertEquals(CareResult.Ok(0), Inbox("""{"items":[],"nextCursor":null}""").source().unreadNoticeCount())
        assertEquals(
            CareResult.Failed(CareFailure.Unavailable),
            Inbox("", HttpStatusCode.InternalServerError).source().unreadNoticeCount(),
        )
    }

    @Test
    fun nothingOfANoticePrints() = runTest {
        val list = assertIs<CareResult.Ok<List<PatientNotice>>>(Inbox(TWO).source().notices()).value
        assertFalse("Hola" in list.toString())
        assertTrue(list.all { it.toString() == "PatientNotice(redacted)" })
    }
}
