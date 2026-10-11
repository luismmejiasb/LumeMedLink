package com.luismejias.lumemedlink.core.device

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The security settings, where fingerprint and face enrolment live on every Android version this app
 * supports (`ACTION_BIOMETRIC_ENROLL` starts at API 30; the floor is 26). A phone whose OEM removed the screen
 * simply does not open — the person is still told what to do.
 */
@Composable
internal actual fun rememberDeviceSettings(): DeviceSettings {
    val context = LocalContext.current
    return remember(context) { DeviceSettings { openSecuritySettings(context) } }
}

private fun openSecuritySettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Nothing to open on this phone; the screen's text already says where to go.
    }
}
