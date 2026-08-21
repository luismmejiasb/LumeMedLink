package com.luismejias.lumemedlink.core.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal actual fun rememberSecureStore(): SecureStore = remember { KeychainSecureStore() }
