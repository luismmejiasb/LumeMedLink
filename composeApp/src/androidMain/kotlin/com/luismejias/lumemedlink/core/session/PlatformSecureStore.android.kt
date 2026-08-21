package com.luismejias.lumemedlink.core.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberSecureStore(): SecureStore {
    val context = LocalContext.current.applicationContext
    return remember { KeystoreSecureStore(context) }
}
