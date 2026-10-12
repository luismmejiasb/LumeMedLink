package com.luismejias.lumemedlink.android

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity
import com.luismejias.lumemedlink.app.App
import com.luismejias.lumemedlink.core.device.AndroidPhotoHost
import com.luismejias.lumemedlink.core.device.LocalAndroidPhotoHost
import com.luismejias.lumemedlink.core.device.decodePickedPhoto
import com.luismejias.lumemedlink.core.input.denyAutofillExport
import com.luismejias.lumemedlink.core.input.denyContentCapture

/**
 * The Android half of the shell. Everything it shows comes from `commonMain`.
 *
 * A [FragmentActivity] rather than a bare ComponentActivity because BiometricPrompt hosts itself
 * in one (F4, ADR-0011). If this base class ever changes back, the tier-2 gate resolves to null
 * and every session ends at the lock screen — loudly, never silently open.
 */
class MainActivity : FragmentActivity() {
    /** Who asked for the photo now on its way; one at a time, and gone with the activity. */
    private var awaitingPhoto: ((Bitmap?) -> Unit)? = null

    // ADR-0050: the system photo picker asks no permission, and the camera's preview returns a bitmap without a file.
    private val gallery = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        deliverPhoto(uri?.let { decodePickedPhoto(it) })
    }
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { deliverPhoto(it) }

    private val photoHost = object : AndroidPhotoHost {
        override val hasCamera: Boolean
            get() = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

        override fun pickFromGallery(onPicked: (Bitmap?) -> Unit) {
            awaitingPhoto = onPicked
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        override fun takePhoto(onTaken: (Bitmap?) -> Unit) {
            awaitingPhoto = onTaken
            camera.launch(null)
        }

        override fun openAppSettings() {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
        }
    }

    private fun deliverPhoto(bitmap: Bitmap?) {
        awaitingPhoto?.invoke(bitmap)
        awaitingPhoto = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // FLAG_SECURE app-wide (ADR-0010): the whole app is personal-data-adjacent — an agenda
        // reveals health (threat model T2) — so screenshots and the recents thumbnail are blocked
        // for every screen, set once here so no screen can forget it. Android CAN block these;
        // iOS cannot (asymmetry 1), where the Compose privacy cover carries the protection.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Tapjacking: drop touches delivered while another window overlays ours.
        window.decorView.filterTouchesWhenObscured = true
        // The OS structure-export channels (F3 reopened, ADR-0024) — a different mechanism from
        // FLAG_SECURE and NOT covered by it. Compose publishes an autofill node for every text
        // field on its own initiative, so the structure went to whichever autofill service the
        // user installed. Two calls rather than one wrapper because the two channels are two
        // mechanisms: the first closes a hole, the second keeps an already-closed one shut.
        // It must be the DECOR view — on the Compose view the assignment is silently discarded
        // (core/input/StructureExport.kt says why).
        window.decorView.denyAutofillExport()
        denyContentCapture()
        setContent { CompositionLocalProvider(LocalAndroidPhotoHost provides photoHost) { App() } }
    }
}
