package com.luismejias.lumemedlink.core.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSBundle

@Composable
internal actual fun rememberAppVersion(): AppVersion = remember {
    val info = NSBundle.mainBundle.infoDictionary
    AppVersion(
        name = info?.get("CFBundleShortVersionString") as? String ?: "",
        build = info?.get("CFBundleVersion") as? String ?: "",
    )
}
