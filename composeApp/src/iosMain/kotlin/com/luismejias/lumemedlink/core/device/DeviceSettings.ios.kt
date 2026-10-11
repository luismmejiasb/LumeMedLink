package com.luismejias.lumemedlink.core.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

/**
 * iOS opens only the app's own page in Settings — Apple offers no public URL to Face ID & Passcode. The screen's
 * text says where to go from there.
 */
@Composable
internal actual fun rememberDeviceSettings(): DeviceSettings = remember { DeviceSettings { openAppSettings() } }

private fun openAppSettings() {
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
}
