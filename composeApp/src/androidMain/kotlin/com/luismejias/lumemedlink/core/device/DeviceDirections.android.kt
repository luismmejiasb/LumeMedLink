package com.luismejias.lumemedlink.core.device

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext

/**
 * ADR-0048's one door: a `geo:` view intent carrying the clinic's address, used to list the apps that answer it and,
 * with the chosen app's package, to open it. Nothing is put on it.
 */
@Composable
internal actual fun rememberDirections(): Directions {
    val context = LocalContext.current
    return remember(context) { AndroidDirections(context) }
}

private class AndroidDirections(private val context: Context) : Directions {
    override fun mapApps(): List<MapApp> {
        val manager = context.packageManager
        return manager.queryIntentActivities(geoIntent(""), PackageManager.MATCH_DEFAULT_ONLY)
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { if (it.activityInfo.packageName == GOOGLE_MAPS) 0 else 1 }
            .map { info ->
                MapApp(
                    id = info.activityInfo.packageName,
                    name = info.loadLabel(manager).toString(),
                    icon = BitmapPainter(info.loadIcon(manager).toBitmap().asImageBitmap()),
                )
            }
    }

    override fun open(app: MapApp, address: String) {
        try {
            context.startActivity(geoIntent(address).setPackage(app.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // Uninstalled between listing and choosing: nothing to open.
        }
    }
}

private fun geoIntent(address: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(GEO_QUERY + Uri.encode(address)))

private fun Drawable.toBitmap(): Bitmap {
    val side = ICON_PIXELS
    val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, side, side)
    draw(Canvas(bitmap))
    return bitmap
}

private const val GEO_QUERY = "geo:0,0?q="
private const val GOOGLE_MAPS = "com.google.android.apps.maps"
private const val ICON_PIXELS = 144
