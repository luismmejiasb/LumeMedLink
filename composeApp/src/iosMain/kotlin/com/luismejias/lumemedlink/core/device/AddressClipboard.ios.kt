package com.luismejias.lumemedlink.core.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.UIKit.UIPasteboard
import platform.UIKit.UIPasteboardOptionExpirationDate
import platform.UIKit.UIPasteboardOptionLocalOnly

/** ADR-0049: local only (Universal Clipboard does not carry it to another device) and gone after two minutes. */
@Composable
internal actual fun rememberAddressClipboard(): AddressClipboard = remember {
    AddressClipboard { address ->
        UIPasteboard.generalPasteboard.setItems(
            listOf(mapOf(PLAIN_TEXT to address)),
            options = mapOf(
                UIPasteboardOptionLocalOnly to true,
                UIPasteboardOptionExpirationDate to NSDate.dateWithTimeIntervalSinceNow(EXPIRY_SECONDS),
            ),
        )
    }
}

private const val PLAIN_TEXT = "public.utf8-plain-text"
private const val EXPIRY_SECONDS = 120.0
