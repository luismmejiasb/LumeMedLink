package com.luismejias.lumemedlink.core.device

import android.content.ClipData
import android.content.ClipboardManager
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * ADR-0049: the clinic's address, marked sensitive. From Android 13 the system then hides it from its clipboard overlay
 * and keyboard suggestions; below 13 there is no such control, and the ADR says so.
 */
@Composable
internal actual fun rememberAddressClipboard(): AddressClipboard {
    val context = LocalContext.current
    return remember(context) {
        AddressClipboard { address ->
            val clip = ClipData.newPlainText("", address)
            // The framework's constant is ClipDescription.EXTRA_IS_SENSITIVE (API 33); its value is written out so
            // the extra is set on every version, and read where the system knows it.
            clip.description.extras = PersistableBundle().apply { putBoolean(IS_SENSITIVE, true) }
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
        }
    }
}

private const val IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
