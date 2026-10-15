package com.luismejias.lumemedlink.core.care

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The platform's side, scripted: what it holds, what it answers to a read, and whether a write goes through. */
private class ScriptedPhotoGateway(
    var held: ByteArray? = null,
    var readAnswer: (() -> PhotoRead)? = null,
    var writes: PhotoWrite = PhotoWrite.DONE,
) : ProfilePhotoGateway {
    val uploads = mutableListOf<ByteArray>()

    override suspend fun read(): PhotoRead =
        readAnswer?.invoke() ?: held?.let { PhotoRead.Present(it) } ?: PhotoRead.Absent

    override suspend fun upload(jpeg: ByteArray): PhotoWrite {
        uploads += jpeg
        if (writes == PhotoWrite.DONE) held = jpeg
        return writes
    }

    override suspend fun remove(): PhotoWrite {
        if (writes == PhotoWrite.DONE) held = null
        return writes
    }
}

// Synthetic bytes, never a face (§9).
private val OLD = byteArrayOf(1, 2, 3)
private val NEW = byteArrayOf(4, 5, 6)

/** The profile photo against the platform (ADR-0064): up, down and away, and what each failure leaves. */
class ProfilePhotoSyncTest {
    @Test
    fun thePlatformsPhotoIsReadAndANewOneReplacesItOnceTaken() = runTest {
        val platform = ScriptedPhotoGateway(held = OLD)
        val sync = ProfilePhotoSync(platform)
        sync.load()
        assertContentEquals(OLD, sync.photo.value)

        assertTrue(sync.upload(NEW))
        assertContentEquals(NEW, sync.photo.value)
        assertContentEquals(NEW, platform.held)
        assertFalse(sync.uploadFailed.value)
    }

    @Test
    fun aFailedUploadKeepsThePreviousPhotoAndTheRetrySendsTheSameOne() = runTest {
        val platform = ScriptedPhotoGateway(held = OLD, writes = PhotoWrite.FAILED)
        val sync = ProfilePhotoSync(platform).apply { load() }

        assertFalse(sync.upload(NEW))
        assertContentEquals(OLD, sync.photo.value, "spec k3: the previous photo stays")
        assertTrue(sync.uploadFailed.value, "«No pudimos subir tu foto · Reintentar»")

        platform.writes = PhotoWrite.DONE
        assertTrue(sync.retry())
        assertContentEquals(NEW, sync.photo.value)
        assertEquals(2, platform.uploads.size)
        assertContentEquals(NEW, platform.uploads.last(), "the retry sends the same photo")
        assertFalse(sync.uploadFailed.value)
    }

    @Test
    fun notNowDropsTheAttemptAndARetryAfterItSendsNothing() = runTest {
        val platform = ScriptedPhotoGateway(writes = PhotoWrite.FAILED)
        val sync = ProfilePhotoSync(platform)
        sync.upload(NEW)
        sync.dropFailedUpload()
        assertFalse(sync.uploadFailed.value)
        assertFalse(sync.retry())
        assertEquals(1, platform.uploads.size)
    }

    @Test
    fun removingBringsTheInitialsBackAndAFailedRemoveKeepsThePhoto() = runTest {
        val platform = ScriptedPhotoGateway(held = OLD)
        val sync = ProfilePhotoSync(platform).apply { load() }

        platform.writes = PhotoWrite.FAILED
        assertFalse(sync.remove())
        assertContentEquals(OLD, sync.photo.value)

        platform.writes = PhotoWrite.DONE
        assertTrue(sync.remove())
        assertNull(sync.photo.value)
        assertNull(platform.held)
    }

    @Test
    fun aRefusedAMissingOrAFailedReadIsTheInitialsInSilence() = runTest {
        // 403 and 404 reach the app as Absent; a transport failure as Failed. All three are no photo, and no error:
        // the sync has no failure state for a read at all.
        for (answer in listOf(PhotoRead.Absent, PhotoRead.Failed)) {
            val sync = ProfilePhotoSync(ScriptedPhotoGateway(readAnswer = { answer }))
            sync.load()
            assertNull(sync.photo.value)
            assertFalse(sync.uploadFailed.value)
        }
    }

    @Test
    fun theSessionsEndLeavesNothingOfThePhoto() = runTest {
        val platform = ScriptedPhotoGateway(held = OLD, writes = PhotoWrite.FAILED)
        val sync = ProfilePhotoSync(platform).apply { load() }
        sync.upload(NEW)

        sync.clear()
        assertNull(sync.photo.value, "§8.13: the bitmap's bytes go with the session")
        assertFalse(sync.uploadFailed.value)
        platform.writes = PhotoWrite.DONE
        assertFalse(sync.retry(), "and the failed attempt with them")
    }

    @Test
    fun theStandInHoldsWhatWasUploadedUntilRemoved() = runTest {
        val standIn = InMemoryProfilePhoto()
        assertEquals(PhotoRead.Absent, standIn.read())
        standIn.upload(NEW)
        assertContentEquals(NEW, (standIn.read() as PhotoRead.Present).jpeg)
        standIn.remove()
        assertEquals(PhotoRead.Absent, standIn.read())
    }

    @Test
    fun aPhotoPrintsNothingOfItself() {
        assertEquals("Present(redacted)", PhotoRead.Present(NEW).toString())
    }
}
