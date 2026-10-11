package com.luismejias.lumemedlink.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The ViewModels of ONE person's main app, kept apart from the shell's. They outlive a rotation (this object is
 * a ViewModel of the activity's store) and die together on [reset] — signing out or starting over — so what one
 * patient's tabs read never survives in memory into the next attempt (§8.13: logout wipes memory too).
 */
internal class SessionScope : ViewModel() {
    private val current = MutableStateFlow(owner())
    val owner: StateFlow<ViewModelStoreOwner> = current.asStateFlow()

    fun reset() {
        current.value.viewModelStore.clear()
        current.value = owner()
    }

    override fun onCleared() {
        current.value.viewModelStore.clear()
    }

    private fun owner(): ViewModelStoreOwner {
        val store = ViewModelStore()
        return object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }
}

/** Every `viewModel { }` inside [content] lands in [scope]'s store, not the activity's. */
@Composable
internal fun InSessionScope(scope: SessionScope, content: @Composable () -> Unit) {
    val owner by scope.owner.collectAsState()
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
