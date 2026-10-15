package com.luismejias.lumemedlink.core.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cl.lume.uicomposer.components.LumeRefresh
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.common_refresh
import com.luismejias.lumemedlink.resources.common_refreshing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.stringResource

/**
 * A tab root's pull to refresh (the author, 2026-10-15): whether one is running, and the run itself. The content stays
 * on screen while it reads — [markLoading] keeps what was shown — and a second pull during a run is ignored.
 */
internal class PullRefresh {
    private val running = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = running.asStateFlow()

    suspend fun run(load: suspend () -> Unit) {
        if (running.value) return
        running.value = true
        try {
            load()
        } finally {
            running.value = false
        }
    }
}

/** The kit's refresh for a [PullRefresh], with the reader's words resolved. */
@Composable
internal fun careRefresh(pull: PullRefresh, onRefresh: () -> Unit): LumeRefresh {
    val refreshing by pull.refreshing.collectAsState()
    return LumeRefresh(
        isRefreshing = refreshing,
        actionLabel = stringResource(Res.string.common_refresh),
        refreshingDescription = stringResource(Res.string.common_refreshing),
        onRefresh = onRefresh,
    )
}
