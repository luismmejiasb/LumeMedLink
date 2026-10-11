package com.luismejias.lumemedlink.core.device

import androidx.compose.runtime.Composable

/**
 * The phone's own dialer and mail app (p6, «Llamar» and «Correo»). Hands over the number or the address a doctor chose
 * to share with their patients and nothing else; the call and the message are the phone's, not this app's. Nothing
 * comes back.
 */
internal interface DeviceContact {
    /** Opens the dialer with [e164] typed in — it does not place the call. */
    fun dial(e164: String)

    /**
     * Opens the mail app addressed to [address], with no subject, body or attachment (ADR-0047). An address that
     * fails [mailtoAddressOrNull] opens nothing.
     */
    fun mail(address: String)
}

/** What can open a `mailto:` query, a fragment or a second recipient: none of it reaches the mail app (ADR-0047). */
private val MAILTO_BREAKERS = setOf('?', '&', '#', '%', ',', ';', '/', '\\', ':', '<', '>', '"')

/**
 * [address] trimmed, when it is one plain address — one `@`, something on each side, and nothing that would let
 * the value add a subject, a body, an attachment or another recipient to the `mailto:`. Otherwise null.
 */
internal fun mailtoAddressOrNull(address: String): String? {
    val trimmed = address.trim()
    val at = trimmed.indexOf('@')
    val plain = trimmed.none { it.isWhitespace() || it.isISOControl() || it in MAILTO_BREAKERS }
    return trimmed.takeIf { plain && at > 0 && at == trimmed.lastIndexOf('@') && at < trimmed.lastIndex }
}

@Composable
internal expect fun rememberDeviceContact(): DeviceContact
