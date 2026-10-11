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
import cl.lume.uicomposer.components.LumeEyebrow
import cl.lume.uicomposer.components.LumeFieldState
import cl.lume.uicomposer.components.LumeList
import cl.lume.uicomposer.components.LumeListAccessory
import cl.lume.uicomposer.components.LumeListRow
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.foundations.LumeIcon
import cl.lume.uicomposer.tokens.LumeTileTint
import com.luismejias.lumemedlink.core.care.RecentSearch
import com.luismejias.lumemedlink.features.patient.parts.CareLoad
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.search_action
import com.luismejias.lumemedlink.resources.search_by_name_body
import com.luismejias.lumemedlink.resources.search_by_name_title
import com.luismejias.lumemedlink.resources.search_choose
import com.luismejias.lumemedlink.resources.search_commune
import com.luismejias.lumemedlink.resources.search_intro
import com.luismejias.lumemedlink.resources.search_recent
import com.luismejias.lumemedlink.resources.search_required_note
import com.luismejias.lumemedlink.resources.search_specialty
import com.luismejias.lumemedlink.resources.tab_search
import org.jetbrains.compose.resources.stringResource

private enum class Criterion { SPECIALTY, COMMUNE }

/**
 * Buscar (p2). The recent searches are the only read; a failure to load them hides the section rather than the
 * form — searching does not depend on them.
 */
@Composable
internal fun SearchScreen(
    model: SearchViewModel,
    onPickSpecialty: () -> Unit,
    onPickCommune: () -> Unit,
    onSearch: () -> Unit,
    onSearchByName: () -> Unit,
) {
    val criteria by model.criteria.collectAsState()
    val recent by model.recent.collectAsState()
    LumeContainer(title = stringResource(Res.string.tab_search), scroll = LumeContainerScroll.Scrollable) {
        LumeStack(alignment = LumeStackAlignment.Start, spacing = { large }) {
            LumeText(text = stringResource(Res.string.search_intro), color = LumeTextColor.Secondary)
            LumeStack(alignment = LumeStackAlignment.Start, spacing = { small }) {
                LumeList(
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
                            Triple(Res.string.search_specialty, criteria.specialty, LumeIcon.Stethoscope)
                        Criterion.COMMUNE -> Triple(Res.string.search_commune, criteria.commune, LumeIcon.Pin)
                    }
                    LumeListRow(
                        title = value ?: stringResource(Res.string.search_choose),
                        description = stringResource(label),
                        leading = LumeListAccessory.Tile(icon, LumeTileTint.Azure),
                        disclosure = true,
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
            val searches = (recent as? CareLoad.Ready)?.content.orEmpty()
            if (searches.isNotEmpty()) {
                LumeStack(alignment = LumeStackAlignment.Start, spacing = { small }) {
                    LumeEyebrow(title = stringResource(Res.string.search_recent))
                    LumeList(
                        items = searches,
                        key = { "${it.specialty}/${it.commune}" },
                        scrolls = false,
                        onRowTap = { search: RecentSearch ->
                            model.recentChosen(search)
                            onSearch()
                        },
                    ) { search ->
                        LumeListRow(title = search.specialty, description = search.commune, disclosure = true)
                    }
                }
            }
            LumeList(items = listOf(Unit), key = { it }, scrolls = false, onRowTap = { onSearchByName() }) {
                LumeListRow(
                    title = stringResource(Res.string.search_by_name_title),
                    description = stringResource(Res.string.search_by_name_body),
                    leading = LumeListAccessory.Icon(LumeIcon.Search, ""),
                    disclosure = true,
                )
            }
        }
    }
}
