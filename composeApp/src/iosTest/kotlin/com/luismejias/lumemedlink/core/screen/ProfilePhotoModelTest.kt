package com.luismejias.lumemedlink.core.screen

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntRect
import com.luismejias.lumemedlink.core.care.InMemoryProfilePhoto
import com.luismejias.lumemedlink.core.care.PhotoRead
import com.luismejias.lumemedlink.core.care.PhotoWrite
import com.luismejias.lumemedlink.core.care.ProfilePhotoGateway
import com.luismejias.lumemedlink.core.device.PhotoResult
import com.luismejias.lumemedlink.core.device.PhotoSource
import com.luismejias.lumemedlink.core.device.platformPhotoCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The platform refuses every write: k3's failure. */
private class RefusingPhotoGateway : ProfilePhotoGateway {
    override suspend fun read(): PhotoRead = PhotoRead.Absent

    override suspend fun upload(jpeg: ByteArray): PhotoWrite = PhotoWrite.FAILED

    override suspend fun remove(): PhotoWrite = PhotoWrite.FAILED
}

// On iOS only: an ImageBitmap needs a real canvas, which the Android host tests do not have. The codec is the real
// one (Skia), so the photo goes out as JPEG and comes back as a bitmap, as it will against the platform.
@OptIn(ExperimentalCoroutinesApi::class)
class ProfilePhotoModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun model(gateway: ProfilePhotoGateway = InMemoryProfilePhoto()) =
        ProfilePhotoModel(gateway, platformPhotoCodec())

    @Test
    fun aPickedPhotoGoesToTheCircleAndThenReplacesTheInitials() {
        val model = model()
        model.openOptions()
        model.picked(PhotoResult.Picked(ImageBitmap(4, 4)))
        assertIs<PhotoStep.Cropping>(model.step.value)
        model.cropped(ImageBitmap(4, 4))
        assertNotNull(model.photo.value, "the platform's bytes, back as a bitmap")
        assertNull(model.step.value)
        assertEquals(PhotoNews.UPDATED, model.news.value, "k3 says «Foto actualizada»")
    }

    @Test
    fun aFailedUploadIsK3sRetryAndKeepsTheInitials() {
        val model = model(RefusingPhotoGateway())
        model.cropped(ImageBitmap(4, 4))
        assertEquals(PhotoStep.UploadFailed, model.step.value)
        assertNull(model.photo.value, "the previous photo — none — stays")
        model.dropFailedUpload()
        assertNull(model.step.value)
    }

    @Test
    fun aRefusedCameraIsK4AndACancelClosesTheFlow() {
        val model = model()
        model.openOptions()
        model.picked(PhotoResult.Denied(PhotoSource.CAMERA))
        assertEquals(PhotoStep.Denied(PhotoSource.CAMERA), model.step.value)
        model.openOptions()
        model.picked(PhotoResult.Cancelled)
        assertNull(model.step.value)
    }

    @Test
    fun removingIsConfirmedAndBringsTheInitialsBack() {
        val model = model()
        model.cropped(ImageBitmap(4, 4))
        model.askRemove()
        assertEquals(PhotoStep.ConfirmRemove, model.step.value, "nothing is removed before the confirmation")
        assertNotNull(model.photo.value)
        model.remove()
        assertNull(model.photo.value)
    }

    @Test
    fun theFramedSquareBecomesTheAvatarScaledDown() {
        val model = model()
        model.picked(PhotoResult.Picked(ImageBitmap(1_200, 900)))
        model.framed(IntRect(100, 50, 900, 850))
        val photo = model.photo.value
        assertEquals(PHOTO_SIDE, photo?.width, "800 px square, compressed to the avatar's side")
        assertEquals(PHOTO_SIDE, photo?.height)
    }

    @Test
    fun aSmallSquareIsNotBlownUp() {
        val model = model()
        model.picked(PhotoResult.Picked(ImageBitmap(300, 300)))
        model.framed(IntRect(0, 0, 200, 200))
        assertEquals(200, model.photo.value?.width)
    }
}
