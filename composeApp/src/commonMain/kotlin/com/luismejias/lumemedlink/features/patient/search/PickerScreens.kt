package com.luismejias.lumemedlink.features.patient.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cl.lume.uicomposer.components.LumeCallout
import cl.lume.uicomposer.components.LumeEmptyState
import cl.lume.uicomposer.components.LumeEyebrow
import cl.lume.uicomposer.components.LumeList
import cl.lume.uicomposer.components.LumeListAccessory
import cl.lume.uicomposer.components.LumeListRow
import cl.lume.uicomposer.components.LumeListSection
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.core.care.Commune
import com.luismejias.lumemedlink.core.care.Specialty
import com.luismejias.lumemedlink.core.input.SensitiveFieldPurpose
import com.luismejias.lumemedlink.core.input.SensitiveTextField
import com.luismejias.lumemedlink.features.patient.parts.CareLoadView
import com.luismejias.lumemedlink.features.patient.parts.PushedScreen
import com.luismejias.lumemedlink.features.patient.parts.folded
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.search_chosen
import com.luismejias.lumemedlink.resources.search_commune
import com.luismejias.lumemedlink.resources.search_commune_note
import com.luismejias.lumemedlink.resources.search_commune_prompt
import com.luismejias.lumemedlink.resources.search_none_match
import com.luismejias.lumemedlink.resources.search_results_count
import com.luismejias.lumemedlink.resources.search_specialty
import com.luismejias.lumemedlink.resources.search_specialty_none_body
import com.luismejias.lumemedlink.resources.search_specialty_none_title
import com.luismejias.lumemedlink.resources.search_specialty_prompt
import com.luismejias.lumemedlink.resources.search_synonyms_note
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The letter groups of e0, as the design cuts the alphabet. */
private val LETTER_GROUPS = listOf('a'..'d', 'e'..'m', 'n'..'p', 'q'..'z')

private fun CharRange.title(): String = "${first.uppercaseChar()} – ${last.uppercaseChar()}"

/**
 * e0, e1, e2. The filter is a field of the hardened primitive (ADR-0013) like every typed text in this app; the
 * kit's search field takes no text case.
 */
@Composable
internal fun SpecialtyScreen(model: SearchViewModel, onBack: () -> Unit) {
    val load by model.specialties.state.collectAsState()
    val criteria by model.criteria.collectAsState()
    PushedScreen(title = stringResource(Res.string.search_specialty), onBack = onBack) {
        CareLoadView(load, onRetry = { model.launch { specialties.load() } }) { content ->
            SensitiveTextField(
                value = content.query,
                onValueChange = model.specialties::queryChanged,
                purpose = SensitiveFieldPurpose.PERSONAL_DATA,
                label = stringResource(Res.string.search_specialty_prompt),
                placeholder = stringResource(Res.string.search_specialty_prompt),
                modifier = Modifier.fillMaxWidth(),
            )
            val matches = content.matches
            val chosenLabel = stringResource(Res.string.search_chosen)
            val pick: (Specialty) -> Unit = { model.launch { specialtyChosen(it) } }
            val row: @Composable (Specialty) -> Unit = { specialty ->
                LumeListRow(
                    title = specialty.name,
                    trailing = if (specialty == criteria.specialty) {
                        LumeListAccessory.Icon(LumeIcon.Check, chosenLabel)
                    } else {
                        LumeListAccessory.None
                    },
                )
            }
            when {
                matches.isEmpty() -> LumeEmptyState(
                    icon = LumeIcon.Search,
                    title = stringResource(Res.string.search_specialty_none_title, content.query.trim()),
                    message = stringResource(Res.string.search_specialty_none_body),
                )
                content.searching -> {
                    LumeEyebrow(pluralStringResource(Res.plurals.search_results_count, matches.size, matches.size))
                    LumeList(items = matches, key = { it.code }, scrolls = false, onRowTap = pick, rowContent = row)
                    LumeCallout(text = stringResource(Res.string.search_synonyms_note))
                }
                else -> LumeList(
                    sections = LETTER_GROUPS.mapNotNull { letters ->
                        val inGroup = matches.filter { it.name.folded().first() in letters }
                        if (inGroup.isEmpty()) null else LumeListSection(letters, inGroup, title = letters.title())
                    },
                    key = { it.code },
                    scrolls = false,
                    onRowTap = pick,
                    rowContent = row,
                )
            }
        }
    }
}

/** e3: grouped by region, only communes with doctors of the chosen specialty. */
@Composable
internal fun CommuneScreen(model: SearchViewModel, onBack: () -> Unit) {
    val load by model.communes.state.collectAsState()
    val criteria by model.criteria.collectAsState()
    PushedScreen(title = stringResource(Res.string.search_commune), onBack = onBack) {
        CareLoadView(load, onRetry = { model.launch { communes.load() } }) { content ->
            SensitiveTextField(
                value = content.query,
                onValueChange = model.communes::queryChanged,
                purpose = SensitiveFieldPurpose.PERSONAL_DATA,
                label = stringResource(Res.string.search_commune_prompt),
                placeholder = stringResource(Res.string.search_commune_prompt),
                modifier = Modifier.fillMaxWidth(),
            )
            val matches = content.matches
            val chosenLabel = stringResource(Res.string.search_chosen)
            if (matches.isEmpty()) {
                LumeCallout(text = stringResource(Res.string.search_none_match))
            } else {
                LumeList(
                    sections = matches.groupBy { it.region }.map { (region, inRegion) ->
                        LumeListSection(region, inRegion, title = region)
                    },
                    key = { it.code },
                    scrolls = false,
                    onRowTap = { commune: Commune -> model.communeChosen(commune) },
                ) { commune ->
                    LumeListRow(
                        title = commune.name,
                        trailing = if (commune == criteria.commune) {
                            LumeListAccessory.Icon(LumeIcon.Check, chosenLabel)
                        } else {
                            LumeListAccessory.None
                        },
                    )
                }
            }
            LumeCallout(text = stringResource(Res.string.search_commune_note))
        }
    }
}
