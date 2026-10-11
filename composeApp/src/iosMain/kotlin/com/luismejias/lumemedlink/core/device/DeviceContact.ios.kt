package com.luismejias.lumemedlink.core.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/** `tel:` asks the person before calling; `mailto:` carries the address and nothing else (ADR-0047). */
@Composable
internal actual fun rememberDeviceContact(): DeviceContact = remember {
    object : DeviceContact {
        override fun dial(e164: String) = open("tel:${e164.filter { it == '+' || it.isDigit() }}")

        override fun mail(address: String) {
            mailtoAddressOrNull(address)?.let { open("mailto:$it") }
        }
    }
}

private fun open(link: String) {
    val url = NSURL.URLWithString(link) ?: return
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
}
