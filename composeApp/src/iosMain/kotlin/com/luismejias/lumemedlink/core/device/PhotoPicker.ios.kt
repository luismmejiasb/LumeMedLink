package com.luismejias.lumemedlink.core.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

/**
 * ADR-0050 on iOS: PHPicker (no photo-library permission; the app sees only the photo chosen) and the camera, which
 * asks the first time (`NSCameraUsageDescription`). The photo is decoded in memory and never written anywhere.
 */
@Composable
internal actual fun rememberPhotoPicker(onResult: (PhotoResult) -> Unit): PhotoPicker {
    val latest = rememberUpdatedState(onResult)
    return remember { IosPhotoPicker { latest.value(it) } }
}

private class IosPhotoPicker(private val deliver: (PhotoResult) -> Unit) : PhotoPicker {
    private val galleryDelegate = GalleryDelegate(::onMain)
    private val cameraDelegate = CameraDelegate(::onMain)

    override val cameraAvailable: Boolean =
        UIImagePickerController.isSourceTypeAvailable(
            UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera,
        )

    override fun pick(source: PhotoSource) = when (source) {
        PhotoSource.GALLERY -> present(
            PHPickerViewController(
                PHPickerConfiguration().apply {
                    filter = PHPickerFilter.imagesFilter
                    selectionLimit = 1
                },
            ).apply { delegate = galleryDelegate },
        )
        PhotoSource.CAMERA -> camera()
    }

    override fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }

    private fun camera() {
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> presentCamera()
            AVAuthorizationStatusNotDetermined -> AVCaptureDevice.requestAccessForMediaType(
                AVMediaTypeVideo,
            ) { granted ->
                dispatch_async(dispatch_get_main_queue()) {
                    if (granted) presentCamera() else deliver(PhotoResult.Denied(PhotoSource.CAMERA))
                }
            }
            else -> deliver(PhotoResult.Denied(PhotoSource.CAMERA))
        }
    }

    private fun presentCamera() = present(
        UIImagePickerController().apply {
            sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            delegate = cameraDelegate
        },
    )

    private fun onMain(result: PhotoResult) {
        dispatch_async(dispatch_get_main_queue()) { deliver(result) }
    }
}

private class GalleryDelegate(private val deliver: (PhotoResult) -> Unit) :
    NSObject(),
    PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val provider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider
        if (provider == null || !provider.hasItemConformingToTypeIdentifier(IMAGE_TYPE)) {
            deliver(PhotoResult.Cancelled)
            return
        }
        // The image's bytes, whatever its format (HEIC included): UIKit decodes them, and they never touch a file.
        provider.loadDataRepresentationForTypeIdentifier(IMAGE_TYPE) { data, _ ->
            val image = data?.let { UIImage.imageWithData(it) }
            deliver(image?.toImageBitmap()?.let { PhotoResult.Picked(it) } ?: PhotoResult.Cancelled)
        }
    }
}

private class CameraDelegate(private val deliver: (PhotoResult) -> Unit) :
    NSObject(),
    UIImagePickerControllerDelegateProtocol,
    UINavigationControllerDelegateProtocol {
    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        deliver(image?.toImageBitmap()?.let { PhotoResult.Picked(it) } ?: PhotoResult.Cancelled)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        deliver(PhotoResult.Cancelled)
    }
}

/** The topmost screen, to present the system's pickers over the app. */
private fun present(controller: UIViewController) {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
    while (true) top = top.presentedViewController ?: break
    top.presentViewController(controller, animated = true, completion = null)
}

@OptIn(ExperimentalForeignApi::class)
private fun UIImage.toImageBitmap(): ImageBitmap? {
    val data: NSData = UIImageJPEGRepresentation(this, JPEG_QUALITY) ?: return null
    val bytes = ByteArray(data.length.toInt())
    bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
    return Image.makeFromEncoded(bytes).toComposeImageBitmap()
}

private const val JPEG_QUALITY = 0.9
private const val IMAGE_TYPE = "public.image"
