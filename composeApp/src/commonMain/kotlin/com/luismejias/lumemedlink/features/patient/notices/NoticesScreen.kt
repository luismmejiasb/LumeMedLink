package com.luismejias.lumemedlink.features.patient.notices

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cl.lume.uicomposer.components.LumeEmptyState
import cl.lume.uicomposer.components.LumeEmptyStateLook
import cl.lume.uicomposer.components.LumeList
import cl.lume.uicomposer.components.LumeListChrome
import cl.lume.uicomposer.components.LumeListRow
import cl.lume.uicomposer.components.LumeListRowTextStyle
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.core.care.PatientNotice
import com.luismejias.lumemedlink.core.screen.CareLoadView
import com.luismejias.lumemedlink.core.screen.shortDateText
import com.luismejias.lumemedlink.features.patient.parts.PushedScreen
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.home_notices
import com.luismejias.lumemedlink.resources.notices_empty_body
import com.luismejias.lumemedlink.resources.notices_empty_title
import org.jetbrains.compose.resources.stringResource

/**
 * The notices the platform sent by e-mail or SMS, newest first, each as it came: its title, its words, the clinic and
 * when (task `0037`). Not designed in the handoff: built from the kit's list, as the other pushed lists are. A notice
 * is read, never acted on, so its rows open nothing.
 */
@Composable
internal fun NoticesScreen(model: NoticesViewModel) {
    val load by model.state.collectAsState()
    val standIn by model.standIn.collectAsState()
    PushedScreen(title = stringResource(Res.string.home_notices), onBack = model::close) {
        CareLoadView(load, standIn, onRetry = model::refresh) { notices ->
            if (notices.isEmpty()) {
                LumeEmptyState(
                    look = LumeEmptyStateLook.Tiled(),
                    icon = LumeIcon.Bell,
                    title = stringResource(Res.string.notices_empty_title),
                    message = stringResource(Res.string.notices_empty_body),
                )
            } else {
                LumeList(items = notices, key = { it.id }, scrolls = false, chrome = LumeListChrome.OpenRuled) {
                    LumeListRow(
                        textStyle = LumeListRowTextStyle.Strong,
                        title = it.title,
                        description = noticeDescription(it),
                    )
                }
            }
        }
    }
}

@Composable
private fun noticeDescription(notice: PatientNotice): String = listOfNotNull(
    notice.text,
    listOf(notice.clinic, shortDateText(notice.sentOn), notice.sentAt.display)
        .filter { it.isNotBlank() }
        .joinToString(" · "),
).joinToString("\n")
