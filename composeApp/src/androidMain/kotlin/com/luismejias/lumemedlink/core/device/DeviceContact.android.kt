package com.luismejias.lumemedlink.core.device

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * `ACTION_DIAL`, not `ACTION_CALL`: the dialer opens with the number and the person decides; no permission. Mail is the
 * one send intent ADR-0047 lets through, in this one shape: an address, and nothing put on the intent.
 */
@Composable
internal actual fun rememberDeviceContact(): DeviceContact {
    val context = LocalContext.current
    return remember(context) {
        object : DeviceContact {
            override fun dial(e164: String) = dial(context, e164)

            override fun mail(address: String) = mail(context, address)
        }
    }
}

private fun dial(context: Context, e164: String) {
    val number = e164.filter { it == '+' || it.isDigit() }
    open(context, Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
}

private fun mail(context: Context, address: String) {
    val plain = mailtoAddressOrNull(address) ?: return
    open(context, Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", plain, null)))
}

private fun open(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No dialer or mail app on this phone (a tablet, a work profile): nothing to open.
    }
}
