package com.luismejias.lumemedlink.core.device

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberAppVersion(): AppVersion {
    val context = LocalContext.current
    return remember(context) {
        val info = packageInfo(context)
        val build = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else legacyCode(info)
        AppVersion(name = info.versionName.orEmpty(), build = build.toString())
    }
}

private fun packageInfo(context: Context): PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
} else {
    legacyPackageInfo(context)
}

// The only call below API 33: its replacement does not exist there.
@Suppress("DEPRECATION")
private fun legacyPackageInfo(context: Context): PackageInfo =
    context.packageManager.getPackageInfo(context.packageName, 0)

// The only reading below API 28: `longVersionCode` does not exist there.
@Suppress("DEPRECATION")
private fun legacyCode(info: PackageInfo): Long = info.versionCode.toLong()
