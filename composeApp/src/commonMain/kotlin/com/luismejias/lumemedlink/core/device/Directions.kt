package com.luismejias.lumemedlink.core.device

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/** A map app that can open an address (f1): its name, and its own icon when the platform hands it over. */
internal class MapApp(
    val id: String,
    val name: String,
    /** Android's icon for the app; null on iOS, which does not let an app read another's (ADR-0048). */
    val icon: Painter?,
) {
    override fun toString(): String = "MapApp(id=$id)"
}

/**
 * f1 «Cómo llegar»: the clinic's address handed to a map app, and nothing else (ADR-0048). Never the patient's own
 * address, never a route from where the patient is.
 */
internal interface Directions {
    /** The installed apps that open an address, the platform's own first. */
    fun mapApps(): List<MapApp>

    /** Opens [address] — the appointment's place — in [app]. */
    fun open(app: MapApp, address: String)
}

@Composable
internal expect fun rememberDirections(): Directions

/** f1 «Copiar dirección»: the clinic's address on the clipboard, marked sensitive (ADR-0049). */
internal fun interface AddressClipboard {
    fun copy(address: String)
}

@Composable
internal expect fun rememberAddressClipboard(): AddressClipboard
