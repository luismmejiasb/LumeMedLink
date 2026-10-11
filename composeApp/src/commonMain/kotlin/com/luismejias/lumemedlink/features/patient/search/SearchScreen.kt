package com.luismejias.lumemedlink.features.patient.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cl.lume.uicomposer.components.LumeButton
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeButtonWidth
import cl.lume.uicomposer.components.LumeCallout
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerScroll
import cl.lume.uicomposer.components.LumeFieldState
import cl.lume.uicomposer.components.LumeList
import cl.lume.uicomposer.components.LumeListAccessory
import cl.lume.uicomposer.components.LumeListChrome
import cl.lume.uicomposer.components.LumeListRow
import cl.lume.uicomposer.components.LumeListRowTextStyle
import cl.lume.uicomposer.components.LumeListTileEmphasis
import cl.lume.uicomposer.components.LumeNavBar
import cl.lume.uicomposer.components.LumeNavBarTitleStyle
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.foundations.LumeIcon
import cl.lume.uicomposer.tokens.LumeTileTint
import com.luismejias.lumemedlink.features.patient.parts.TabBarClearance
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.search_action
import com.luismejias.lumemedlink.resources.search_choose
import com.luismejias.lumemedlink.resources.search_commune
import com.luismejias.lumemedlink.resources.search_intro
import com.luismejias.lumemedlink.resources.search_required_note
import com.luismejias.lumemedlink.resources.search_specialty
import com.luismejias.lumemedlink.resources.tab_search
import org.jetbrains.compose.resources.stringResource

private enum class Criterion { SPECIALTY, COMMUNE }

/**
 * Buscar (p2). «¿Ya sabes a quién buscas?» waits: the design has no screen for a search by name (the contract
 * does take a `name`). No recent searches, by the author's call (2026-10-11): a specialty with a commune says something
 * about the searcher's health, and nothing keeps it — not the phone, not the server.
 */
@Composable
internal fun SearchScreen(
    model: SearchViewModel,
    onPickSpecialty: () -> Unit,
    onPickCommune: () -> Unit,
    onSearch: () -> Unit,
) {
    val criteria by model.criteria.collectAsState()
    LumeContainer(
        scroll = LumeContainerScroll.Scrollable,
        topBar = {
            LumeNavBar(title = stringResource(Res.string.tab_search), titleStyle = LumeNavBarTitleStyle.Root)
        },
    ) {
        LumeStack(alignment = LumeStackAlignment.Start, spacing = { large }) {
            LumeText(text = stringResource(Res.string.search_intro), color = LumeTextColor.Secondary)
            LumeStack(alignment = LumeStackAlignment.Start, spacing = { small }) {
                LumeList(
                    chrome = LumeListChrome.Open,
                    items = Criterion.entries,
                    key = { it },
                    scrolls = false,
                    onRowTap = {
                        when (it) {
                            Criterion.SPECIALTY -> onPickSpecialty()
                            Criterion.COMMUNE -> onPickCommune()
                        }
                    },
                ) { criterion ->
                    val (label, value, icon) = when (criterion) {
                        Criterion.SPECIALTY ->
                            Triple(Res.string.search_specialty, criteria.specialty?.name, LumeIcon.Stethoscope)
                        Criterion.COMMUNE -> Triple(Res.string.search_commune, criteria.commune?.name, LumeIcon.Pin)
                    }
                    LumeListRow(
                        title = stringResource(label),
                        description = value ?: stringResource(Res.string.search_choose),
                        leading = LumeListAccessory.Tile(
                            icon,
                            LumeTileTint.Azure,
                            emphasis = LumeListTileEmphasis.Washed,
                        ),
                        disclosure = true,
                        textStyle = LumeListRowTextStyle.LabelOverValue,
                    )
                }
                LumeCallout(text = stringResource(Res.string.search_required_note))
            }
            LumeButton(
                content = LumeButtonContent.Title(stringResource(Res.string.search_action)),
                width = LumeButtonWidth.Fill,
                state = if (criteria.complete) LumeFieldState.Enabled else LumeFieldState.Disabled,
                onClick = onSearch,
            )
            TabBarClearance()
        }
    }
}
