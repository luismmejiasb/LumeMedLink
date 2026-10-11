package com.luismejias.lumemedlink.core.device

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** `ACTION_DIAL`, not `ACTION_CALL`: the dialer opens with the number and the person decides; no permission. */
@Composable
internal actual fun rememberDeviceContact(): DeviceContact {
    val context = LocalContext.current
    return remember(context) { DeviceContact { dial(context, it) } }
}

private fun dial(context: Context, e164: String) {
    try {
        val number = e164.filter { it == '+' || it.isDigit() }
        context.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        // No dialer on this phone (a tablet, a work profile): nothing to open.
    }
}
