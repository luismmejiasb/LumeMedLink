package com.luismejias.lumemedlink.core.screen

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntRect
import com.luismejias.lumemedlink.core.device.PhotoResult
import com.luismejias.lumemedlink.core.device.PhotoSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// On iOS only: an ImageBitmap needs a real canvas, which the Android host tests do not have.
class ProfilePhotoModelTest {
    private val model = ProfilePhotoModel()
    private val image = ImageBitmap(4, 4)

    @Test
    fun aPickedPhotoGoesToTheCircleAndThenReplacesTheInitials() {
        model.openOptions()
        model.picked(PhotoResult.Picked(image))
        assertIs<PhotoStep.Cropping>(model.step.value)
        model.cropped(image)
        assertSame(image, model.photo.value)
        assertNull(model.step.value)
        assertTrue(model.updated.value, "k3 says «Foto actualizada»")
    }

    @Test
    fun aRefusedCameraIsK4AndACancelClosesTheFlow() {
        model.openOptions()
        model.picked(PhotoResult.Denied(PhotoSource.CAMERA))
        assertEquals(PhotoStep.Denied(PhotoSource.CAMERA), model.step.value)
        model.openOptions()
        model.picked(PhotoResult.Cancelled)
        assertNull(model.step.value)
    }

    @Test
    fun removingThePhotoBringsTheInitialsBack() {
        model.cropped(image)
        model.remove()
        assertNull(model.photo.value)
    }

    @Test
    fun theFramedSquareBecomesTheAvatarScaledDown() {
        val large = ImageBitmap(1_200, 900)
        model.picked(PhotoResult.Picked(large))
        model.framed(IntRect(100, 50, 900, 850))
        val photo = model.photo.value
        assertEquals(PHOTO_SIDE, photo?.width, "800 px square, compressed to the avatar's side")
        assertEquals(PHOTO_SIDE, photo?.height)
        assertTrue(model.updated.value)
    }

    @Test
    fun aSmallSquareIsNotBlownUp() {
        model.picked(PhotoResult.Picked(ImageBitmap(300, 300)))
        model.framed(IntRect(0, 0, 200, 200))
        assertEquals(200, model.photo.value?.width)
    }
}
