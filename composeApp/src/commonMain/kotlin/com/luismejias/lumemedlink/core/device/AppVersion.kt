package com.luismejias.lumemedlink.core.device

import androidx.compose.runtime.Composable

/** The installed build, as the stores show it: the version name and, apart, the build number. */
internal data class AppVersion(val name: String, val build: String)

/** Read from the platform's own record of the installed app (the bundle, the package), never from a constant here. */
@Composable
internal expect fun rememberAppVersion(): AppVersion
