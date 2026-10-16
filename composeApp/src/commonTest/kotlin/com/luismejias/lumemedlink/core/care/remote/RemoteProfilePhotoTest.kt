package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.PhotoRead
import com.luismejias.lumemedlink.core.care.PhotoWrite
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.NO_SECURITY_EVENTS
import com.luismejias.lumemedlink.core.networking.NetworkLogEntry
import com.luismejias.lumemedlink.core.networking.NetworkLogSink
import com.luismejias.lumemedlink.core.networking.lumeHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class PhotoQuietLog : NetworkLogSink {
    override fun log(entry: NetworkLogEntry) = Unit
}

/** Not a real JPEG: the platform decodes, the app only carries bytes. Synthetic (§9). */
private val PHOTO_BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())

private val PHOTO_FACTS = """{"width":512,"height":512,"byteSize":7,"updatedAt":"2026-10-16T12:00:00.000Z"}"""

/** One answer for every request, and what was sent: verb, path, content type and bytes. */
private class PhotoPlatform(
    private val status: HttpStatusCode,
    private val body: ByteArray = ByteArray(0),
    private val type: String = "image/jpeg",
) {
    val sent = mutableListOf<Pair<String, Pair<String?, ByteArray>>>()

    fun gateway(): RemoteProfilePhoto = RemoteProfilePhoto(
        ContractClient(
            lumeHttpClient("https://api.test.lume", engine(), PhotoQuietLog(), securityEvents = NO_SECURITY_EVENTS),
        ),
    )

    fun engine(): MockEngine = MockEngine { request ->
        val bytes = request.body.toByteArray()
        sent += "${request.method.value} ${request.url.encodedPath}" to (request.body.contentType?.toString() to bytes)
        val answerType = if (status.value >= 400) "application/problem+json" else type
        respond(body, status, headersOf(HttpHeaders.ContentType, answerType))
    }
}

class RemoteProfilePhotoTest {
    @Test
    fun aPhotoIsReadAsItsBytes() = runTest {
        val read = assertIs<PhotoRead.Present>(PhotoPlatform(HttpStatusCode.OK, PHOTO_BYTES).gateway().read())
        assertContentEquals(PHOTO_BYTES, read.jpeg)
    }

    @Test
    fun noPhotoAndARefusalAreTheInitialsAndAnythingElseIsAFailure() = runTest {
        assertEquals(PhotoRead.Absent, PhotoPlatform(HttpStatusCode.NotFound).gateway().read())
        assertEquals(PhotoRead.Absent, PhotoPlatform(HttpStatusCode.Forbidden).gateway().read())
        assertEquals(PhotoRead.Failed, PhotoPlatform(HttpStatusCode.ServiceUnavailable).gateway().read())
    }

    @Test
    fun aSuccessThatIsNotAnImageIsAFailureNotAPhoto() = runTest {
        val html = "<html>wifi</html>".encodeToByteArray()
        assertEquals(PhotoRead.Failed, PhotoPlatform(HttpStatusCode.OK, html, "text/html").gateway().read())
        val json = "{}".encodeToByteArray()
        assertEquals(PhotoRead.Failed, PhotoPlatform(HttpStatusCode.OK, json, "application/json").gateway().read())
    }

    @Test
    fun theCropIsSentAsRawJpegBytes() = runTest {
        val platform = PhotoPlatform(HttpStatusCode.OK, PHOTO_FACTS.encodeToByteArray(), "application/json")
        assertEquals(PhotoWrite.DONE, platform.gateway().upload(PHOTO_BYTES))
        val (route, body) = platform.sent.single()
        assertEquals("${HttpMethod.Put.value} /v1/patient/photo", route)
        assertEquals("image/jpeg", body.first)
        assertContentEquals(PHOTO_BYTES, body.second)
    }

    @Test
    fun aRefusedPhotoIsAFailedUpload() = runTest {
        listOf(HttpStatusCode.UnsupportedMediaType, HttpStatusCode.UnprocessableEntity, HttpStatusCode.PayloadTooLarge)
            .forEach { assertEquals(PhotoWrite.FAILED, PhotoPlatform(it).gateway().upload(PHOTO_BYTES), "$it") }
    }

    @Test
    fun removingAPhotoThatIsNotThereLeavesNone() = runTest {
        assertEquals(PhotoWrite.DONE, PhotoPlatform(HttpStatusCode.NoContent).gateway().remove())
        assertEquals(PhotoWrite.DONE, PhotoPlatform(HttpStatusCode.NotFound).gateway().remove())
        assertEquals(PhotoWrite.FAILED, PhotoPlatform(HttpStatusCode.ServiceUnavailable).gateway().remove())
    }

    @Test
    fun aProfessionalsPhotoIsReadByTheirIdAndAnyRefusalIsTheInitials() = runTest {
        val platform = PhotoPlatform(HttpStatusCode.OK, PHOTO_BYTES)
        val contract = ContractClient(
            lumeHttpClient(
                "https://api.test.lume",
                platform.engine(),
                PhotoQuietLog(),
                securityEvents = NO_SECURITY_EVENTS,
            ),
        )
        val read = assertIs<PhotoRead.Present>(RemoteProfessionalPhoto(contract).professionalPhoto("pro-1"))
        assertContentEquals(PHOTO_BYTES, read.jpeg)
        assertEquals("GET /v1/patient/professionals/pro-1/photo", platform.sent.single().first)

        val none = PhotoPlatform(HttpStatusCode.NotFound)
        val absent = ContractClient(
            lumeHttpClient(
                "https://api.test.lume",
                none.engine(),
                PhotoQuietLog(),
                securityEvents = NO_SECURITY_EVENTS,
            ),
        )
        assertEquals(PhotoRead.Absent, RemoteProfessionalPhoto(absent).professionalPhoto("pro-1"))
    }
}
