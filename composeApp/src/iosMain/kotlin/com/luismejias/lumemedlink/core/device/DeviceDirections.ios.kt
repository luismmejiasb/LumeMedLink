package com.luismejias.lumemedlink.core.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.URLQueryAllowedCharacterSet
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.UIKit.UIApplication

/**
 * ADR-0048: Apple Maps always, and Google Maps and Waze when `canOpenURL` finds them (their schemes, and only theirs,
 * are in `LSApplicationQueriesSchemes`). iOS does not let an app read another's icon, so none is handed over.
 */
@Composable
internal actual fun rememberDirections(): Directions = remember { IosDirections() }

private class IosDirections : Directions {
    override fun mapApps(): List<MapApp> = APPS.filter { app ->
        app.probe == null ||
            NSURL.URLWithString(app.probe)?.let { UIApplication.sharedApplication.canOpenURL(it) } == true
    }.map { MapApp(it.id, it.name, icon = null) }

    override fun open(app: MapApp, address: String) {
        val known = APPS.firstOrNull { it.id == app.id } ?: return

        @Suppress("CAST_NEVER_SUCCEEDS")
        val query = (address as NSString)
            .stringByAddingPercentEncodingWithAllowedCharacters(NSCharacterSet.URLQueryAllowedCharacterSet) ?: return
        val url = NSURL.URLWithString(known.link + query) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}

private class IosMapApp(val id: String, val name: String, val link: String, val probe: String?)

private val APPS = listOf(
    IosMapApp("apple-maps", "Mapas", "https://maps.apple.com/?q=", probe = null),
    IosMapApp("google-maps", "Google Maps", "comgooglemaps://?q=", probe = "comgooglemaps://"),
    IosMapApp("waze", "Waze", "https://waze.com/ul?q=", probe = "waze://"),
)
