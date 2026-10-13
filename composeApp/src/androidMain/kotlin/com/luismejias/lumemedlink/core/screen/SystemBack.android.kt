package com.luismejias.lumemedlink.core.screen

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/** The activity's back dispatcher, through `androidx.activity`, which this module already compiles against. */
@Composable
internal actual fun SystemBack(onBack: (() -> Unit)?) {
    val dispatcher = LocalContext.current.componentActivity()?.onBackPressedDispatcher ?: return
    val current by rememberUpdatedState(onBack)
    val callback = remember {
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                current?.invoke()
            }
        }
    }
    SideEffect { callback.isEnabled = onBack != null }
    DisposableEffect(dispatcher, callback) {
        dispatcher.addCallback(callback)
        onDispose { callback.remove() }
    }
}

private tailrec fun Context.componentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.componentActivity()
    else -> null
}
