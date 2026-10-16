package com.luismejias.lumemedlink.core.screen

import androidx.compose.ui.graphics.ImageBitmap
import com.luismejias.lumemedlink.core.care.PhotoRead
import com.luismejias.lumemedlink.core.care.ProfessionalPhotoSource
import com.luismejias.lumemedlink.core.device.PhotoCodec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Decodes nothing (a bitmap needs a platform canvas), and counts what it was handed. */
private class CountingCodec : PhotoCodec {
    var decoded = 0

    override fun encode(image: ImageBitmap): ByteArray? = null

    override fun decode(jpeg: ByteArray): ImageBitmap? {
        decoded++
        return null
    }
}

/** Holds every read until [release]; counts reads by id and the most running at once. */
private class HeldPhotos : ProfessionalPhotoSource {
    val asked = mutableListOf<String>()
    var running = 0
    var mostAtOnce = 0
    private val gate = CompletableDeferred<Unit>()

    fun release() = gate.complete(Unit)

    override suspend fun professionalPhoto(professionalId: String): PhotoRead {
        asked += professionalId
        running++
        mostAtOnce = maxOf(mostAtOnce, running)
        gate.await()
        running--
        return if (professionalId == "p-none") PhotoRead.Absent else PhotoRead.Present(byteArrayOf(1))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DoctorPhotosTest {
    @BeforeTest
    fun mainDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun resetDispatcher() = Dispatchers.resetMain()

    @Test
    fun eachProfessionalIsReadOncePerSessionWithAFewAtATime() = runTest {
        val source = HeldPhotos()
        val codec = CountingCodec()
        val photos = DoctorPhotos(source, codec)
        val ids = (1..10).map { "p-$it" }
        ids.forEach { photos.photoOf(it) }
        ids.forEach { photos.photoOf(it) }
        advanceUntilIdle()
        assertEquals(MAX_PHOTO_READS, source.mostAtOnce, "the platform has no batch read: a few at a time")
        source.release()
        advanceUntilIdle()
        assertEquals(ids, source.asked, "asked twice, read once")
        assertEquals(ids.size, codec.decoded)
    }

    @Test
    fun noPhotoStaysTheInitialsAndIsNeverDecoded() = runTest {
        val source = HeldPhotos().also { it.release() }
        val codec = CountingCodec()
        val photo = DoctorPhotos(source, codec).photoOf("p-none")
        advanceUntilIdle()
        assertNull(photo.value)
        assertEquals(0, codec.decoded)
    }
}
