package com.luismejias.lumemedlink.features.patient.parts

import androidx.compose.runtime.Composable
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerActionStyle
import cl.lume.uicomposer.components.LumeContainerScroll
import cl.lume.uicomposer.components.LumeNavBar
import cl.lume.uicomposer.components.LumeNavBarBack
import cl.lume.uicomposer.components.LumeNavBarTitleStyle
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.common_back
import org.jetbrains.compose.resources.stringResource

/**
 * A screen pushed onto a tab's stack: the kit's nav bar with its back disc, over the region's content. The bar
 * sits inside the container because the container is what keeps clear of the status bar.
 */
@Composable
internal fun PushedScreen(
    title: String,
    onBack: () -> Unit,
    scroll: LumeContainerScroll = LumeContainerScroll.Scrollable,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    LumeContainer(scroll = scroll, actionStyle = LumeContainerActionStyle.Bar, action = action) {
        LumeStack(alignment = LumeStackAlignment.Start, spacing = { medium }) {
            LumeNavBar(
                title = title,
                back = LumeNavBarBack(stringResource(Res.string.common_back), onBack),
                titleStyle = LumeNavBarTitleStyle.Inline,
            )
            content()
        }
    }
}
