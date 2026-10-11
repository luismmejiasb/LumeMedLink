package com.luismejias.lumemedlink.core.device

import androidx.compose.runtime.Composable

/**
 * The phone's own dialer (p6, "Llamar"). Hands over the number a doctor chose to share with their patients and
 * nothing else; the call is the phone's, not this app's. Nothing comes back.
 *
 * The design's "Correo" is NOT here: handing an address to the mail app is Android's send intent, one of the
 * doors ADR-0007 closes with no exceptions (check-forbidden-patterns P2). It waits for the author's decision.
 */
internal fun interface DeviceContact {
    /** Opens the dialer with [e164] typed in — it does not place the call. */
    fun dial(e164: String)
}

@Composable
internal expect fun rememberDeviceContact(): DeviceContact
