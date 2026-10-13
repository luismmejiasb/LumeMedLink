package com.luismejias.lumemedlink.core.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeCallout
import cl.lume.uicomposer.components.LumeCalloutSurface
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerAlignment
import cl.lume.uicomposer.components.LumeEmptyState
import cl.lume.uicomposer.components.LumeEmptyStateLook
import cl.lume.uicomposer.components.LumeEmptyStateTone
import cl.lume.uicomposer.components.LumePlaceholder
import cl.lume.uicomposer.components.LumeSkeleton
import cl.lume.uicomposer.components.LumeSkeletonShape
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.foundations.LumeIcon
import cl.lume.uicomposer.tokens.LumeSpacing
import com.luismejias.lumemedlink.core.care.CareFailure
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.care_loading
import com.luismejias.lumemedlink.resources.care_offline_body
import com.luismejias.lumemedlink.resources.care_offline_title
import com.luismejias.lumemedlink.resources.care_unavailable_body
import com.luismejias.lumemedlink.resources.care_unavailable_title
import com.luismejias.lumemedlink.resources.common_retry
import com.luismejias.lumemedlink.resources.offline_title
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/**
 * What a screen waits as (ADR-0055): its own content, read once from the stand-in world ([standInPatientCare]), so the
 * kit's placeholder draws the shape of what is coming. Filled before the real read starts, and never shown as data.
 */
internal class StandIn<T> {
    private val held = MutableStateFlow<T?>(null)
    val content: StateFlow<T?> = held.asStateFlow()

    suspend fun fill(read: suspend () -> CareLoad<T>) {
        if (held.value == null) held.value = (read() as? CareLoad.Ready)?.content
    }
}

/** Sets a load flow to [CareLoad.Loading] unless it already holds content: a retry keeps what was shown. */
internal fun <T> MutableStateFlow<CareLoad<T>>.markLoading() {
    if (value !is CareLoad.Ready) value = CareLoad.Loading
}

/**
 * Renders a read: [content] once it is ready, and while it waits the same [content] drawn from [standIn] under the
 * kit's placeholder — a skeleton of the screen itself, never a spinner (ADR-0055). The two states are one tree, so the
 * arrival is a fill-in rather than a jump. A failure shows its state with the retry. [standalone] wraps the failure in
 * its own centred region (a tab root); inside a pushed screen it sits in the screen's region. [offlineTitle] and
 * [offlineBody] replace the generic words where the design names what failed (e6).
 */
@Composable
internal fun <T> CareLoadView(
    load: CareLoad<T>,
    standIn: T?,
    onRetry: () -> Unit,
    standalone: Boolean = false,
    /**
     * The gap of the stack this sits in, when not [standalone]. The kit's placeholder is one box, so the pieces a
     * screen hands here are laid out again in a stack of their own, with the gap they had in the screen's.
     */
    spacing: LumeSpacing.() -> Dp = { medium },
    offlineTitle: StringResource = Res.string.care_offline_title,
    offlineBody: StringResource = Res.string.care_offline_body,
    content: @Composable (T) -> Unit,
) {
    val state: @Composable () -> Unit = {
        when (load) {
            // Only until the stand-in is read, which the script answers at once: the kit's generic rows, still no
            // spinner.
            CareLoad.Loading -> LumeSkeleton(LumeSkeletonShape.List(rows = STAND_IN_ROWS))
            is CareLoad.Failed -> {
                val offline = load.failure == CareFailure.Offline
                // e6: the dark band first, then the state — a reader hears the band as it appears.
                if (offline) {
                    LumeCallout(
                        stringResource(Res.string.offline_title),
                        surface = LumeCalloutSurface.Inverted,
                        icon = LumeIcon.Wifi,
                    )
                }
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
    val shown = (load as? CareLoad.Ready)?.content ?: standIn.takeIf { load is CareLoad.Loading }
    when {
        shown != null -> LumePlaceholder(
            stringResource(Res.string.care_loading),
            modifier = Modifier.fillMaxWidth(),
            active = load !is CareLoad.Ready,
        ) {
            // A tab root hands one region; a pushed screen hands loose pieces, which a box would draw on top of
            // each other (seen on the appointment's detail, 2026-10-13).
            if (standalone) {
                content(shown)
            } else {
                LumeStack(Modifier.fillMaxWidth(), alignment = LumeStackAlignment.Start, spacing = spacing) {
                    content(shown)
                }
            }
        }
        standalone -> LumeContainer(alignment = LumeContainerAlignment.Center) { state() }
        else -> LumeStack(modifier = Modifier.fillMaxWidth(), spacing = { medium }) { state() }
    }
}

private const val STAND_IN_ROWS = 4
