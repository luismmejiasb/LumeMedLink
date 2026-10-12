package com.luismejias.lumemedlink.core.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cl.lume.uicomposer.components.LumeActivityIndicator
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerAlignment
import cl.lume.uicomposer.components.LumeEmptyState
import cl.lume.uicomposer.components.LumeEmptyStateLook
import cl.lume.uicomposer.components.LumeEmptyStateTone
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.core.care.CareFailure
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.care_loading
import com.luismejias.lumemedlink.resources.care_offline_body
import com.luismejias.lumemedlink.resources.care_offline_title
import com.luismejias.lumemedlink.resources.care_unavailable_body
import com.luismejias.lumemedlink.resources.care_unavailable_title
import com.luismejias.lumemedlink.resources.common_retry
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What a tab root holds while it reads: nothing yet, a failure with its retry, or the content. */
internal sealed interface CareLoad<out T> {
    data object Loading : CareLoad<Nothing>

    data class Failed(val failure: CareFailure) : CareLoad<Nothing>

    data class Ready<T>(val content: T) : CareLoad<T>
}

/** Turns a gateway answer into a [CareLoad]. */
internal fun <T, R> CareResult<T>.toLoad(transform: (T) -> R): CareLoad<R> = when (this) {
    is CareResult.Ok -> CareLoad.Ready(transform(value))
    is CareResult.Failed -> CareLoad.Failed(failure)
}

/** Several reads that must all succeed, or the first failure is what the screen shows. */
internal inline fun <R> allOf(vararg results: CareResult<*>, build: () -> R): CareLoad<R> =
    results.firstNotNullOfOrNull { (it as? CareResult.Failed)?.failure }
        ?.let { CareLoad.Failed(it) }
        ?: CareLoad.Ready(build())

/** Reads `value` of a result already checked by [allOf]. */
internal val <T> CareResult<T>.ok: T get() = (this as CareResult.Ok<T>).value

/** Sets a load flow to [CareLoad.Loading] unless it already holds content: a retry keeps what was shown. */
internal fun <T> MutableStateFlow<CareLoad<T>>.markLoading() {
    if (value !is CareLoad.Ready) value = CareLoad.Loading
}

/**
 * Renders the waiting and failed states of a read, and [content] once it is ready. [standalone] wraps the two
 * states in their own centred region (a tab root); inside a pushed screen they sit in the screen's region.
 * [offlineTitle] and [offlineBody] replace the generic words where the design names what failed (e6).
 */
@Composable
internal fun <T> CareLoadView(
    load: CareLoad<T>,
    onRetry: () -> Unit,
    standalone: Boolean = false,
    offlineTitle: StringResource = Res.string.care_offline_title,
    offlineBody: StringResource = Res.string.care_offline_body,
    content: @Composable (T) -> Unit,
) {
    val state: @Composable () -> Unit = {
        when (load) {
            CareLoad.Loading -> LumeActivityIndicator(contentDescription = stringResource(Res.string.care_loading))
            is CareLoad.Failed -> {
                val offline = load.failure == CareFailure.Offline
                LumeEmptyState(
                    look = LumeEmptyStateLook.Tiled(LumeEmptyStateTone.Error),
                    icon = if (offline) LumeIcon.Wifi else LumeIcon.Alert,
                    title = stringResource(if (offline) offlineTitle else Res.string.care_unavailable_title),
                    message = stringResource(if (offline) offlineBody else Res.string.care_unavailable_body),
                    action = LumeButtonContent.Title(stringResource(Res.string.common_retry)),
                    onAction = onRetry,
                )
            }
            is CareLoad.Ready -> Unit
        }
    }
    when {
        load is CareLoad.Ready -> content(load.content)
        standalone -> LumeContainer(alignment = LumeContainerAlignment.Center) { state() }
        else -> LumeStack(modifier = Modifier.fillMaxWidth(), spacing = { medium }) { state() }
    }
}
