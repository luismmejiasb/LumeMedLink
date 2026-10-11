package com.luismejias.lumemedlink.core.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/** `tel:` asks the person before calling. */
@Composable
internal actual fun rememberDeviceContact(): DeviceContact = remember {
    DeviceContact { e164 ->
        val url = NSURL.URLWithString("tel:${e164.filter { it == '+' || it.isDigit() }}") ?: return@DeviceContact
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}
