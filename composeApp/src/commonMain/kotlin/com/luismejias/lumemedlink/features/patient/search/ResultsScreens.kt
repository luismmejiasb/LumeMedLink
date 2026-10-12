package com.luismejias.lumemedlink.features.patient.search

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cl.lume.uicomposer.components.LumeButton
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeButtonEmphasis
import cl.lume.uicomposer.components.LumeButtonSize
import cl.lume.uicomposer.components.LumeButtonWidth
import cl.lume.uicomposer.components.LumeCallout
import cl.lume.uicomposer.components.LumeCard
import cl.lume.uicomposer.components.LumeCardStyle
import cl.lume.uicomposer.components.LumeChip
import cl.lume.uicomposer.components.LumeChipStyle
import cl.lume.uicomposer.components.LumeDivider
import cl.lume.uicomposer.components.LumeEmptyState
import cl.lume.uicomposer.components.LumeEyebrow
import cl.lume.uicomposer.components.LumeFieldState
import cl.lume.uicomposer.components.LumeFilterChip
import cl.lume.uicomposer.components.LumeList
import cl.lume.uicomposer.components.LumeListAccessory
import cl.lume.uicomposer.components.LumeListChrome
import cl.lume.uicomposer.components.LumeListRow
import cl.lume.uicomposer.components.LumeListSection
import cl.lume.uicomposer.components.LumeListSelection
import cl.lume.uicomposer.components.LumeRating
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeStackAxis
import cl.lume.uicomposer.components.LumeStackStyle
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTextStyle
import cl.lume.uicomposer.components.LumeTextWeight
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.core.care.DirectoryDoctor
import com.luismejias.lumemedlink.core.care.Slot
import com.luismejias.lumemedlink.features.patient.parts.CareLoadView
import com.luismejias.lumemedlink.features.patient.parts.DoctorIdentity
import com.luismejias.lumemedlink.features.patient.parts.PushedScreen
import com.luismejias.lumemedlink.features.patient.parts.capitalized
import com.luismejias.lumemedlink.features.patient.parts.relativeDayText
import com.luismejias.lumemedlink.features.patient.parts.shortDateText
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.filters_apply
import com.luismejias.lumemedlink.resources.filters_attention
import com.luismejias.lumemedlink.resources.filters_availability
import com.luismejias.lumemedlink.resources.filters_clear
import com.luismejias.lumemedlink.resources.filters_four_stars
import com.luismejias.lumemedlink.resources.filters_sort
import com.luismejias.lumemedlink.resources.rating_spoken
import com.luismejias.lumemedlink.resources.results_book
import com.luismejias.lumemedlink.resources.results_change
import com.luismejias.lumemedlink.resources.results_count
import com.luismejias.lumemedlink.resources.results_empty_body
import com.luismejias.lumemedlink.resources.results_empty_title
import com.luismejias.lumemedlink.resources.results_filtered_empty
import com.luismejias.lumemedlink.resources.results_filters
import com.luismejias.lumemedlink.resources.results_filters_count
import com.luismejias.lumemedlink.resources.results_in
import com.luismejias.lumemedlink.resources.results_nearby
import com.luismejias.lumemedlink.resources.results_nearby_row
import com.luismejias.lumemedlink.resources.results_next
import com.luismejias.lumemedlink.resources.results_no_slot
import com.luismejias.lumemedlink.resources.results_offline_body
import com.luismejias.lumemedlink.resources.results_offline_title
import com.luismejias.lumemedlink.resources.results_remove_filter
import com.luismejias.lumemedlink.resources.results_sorted_attended
import com.luismejias.lumemedlink.resources.results_sorted_best
import com.luismejias.lumemedlink.resources.results_sorted_soonest
import com.luismejias.lumemedlink.resources.results_title
import com.luismejias.lumemedlink.resources.sort_attended
import com.luismejias.lumemedlink.resources.sort_best
import com.luismejias.lumemedlink.resources.sort_soonest
import com.luismejias.lumemedlink.resources.window_any
import com.luismejias.lumemedlink.resources.window_week
import com.luismejias.lumemedlink.shared.CalendarDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val SearchSort.label: StringResource
    get() = when (this) {
        SearchSort.SOONEST -> Res.string.sort_soonest
        SearchSort.BEST_RATED -> Res.string.sort_best
        SearchSort.MOST_ATTENDED -> Res.string.sort_attended
    }

private val SearchSort.caption: StringResource
    get() = when (this) {
        SearchSort.SOONEST -> Res.string.results_sorted_soonest
        SearchSort.BEST_RATED -> Res.string.results_sorted_best
        SearchSort.MOST_ATTENDED -> Res.string.results_sorted_attended
    }

/** "Mañana · 09:00", "Vie 18 · 15:30". */
@Composable
internal fun slotText(slot: Slot, today: CalendarDate): String =
    "${relativeDayText(slot.date, today) ?: shortDateText(slot.date).capitalized()} · ${slot.start.display}"

/**
 * p3, e5, e6. PROVISIONAL (ADR-0046): the rating, its count and the number of visits have no contract
 * (request 0008 §4). «Agendar» and the doctor's row both open a1.
 */
@Composable
internal fun ResultsScreen(model: SearchViewModel, onBack: () -> Unit) {
    val load by model.results.state.collectAsState()
    val criteria by model.criteria.collectAsState()
    PushedScreen(title = stringResource(Res.string.results_title), onBack = onBack) {
        val specialty = criteria.specialty?.name.orEmpty()
        val commune = criteria.commune?.name.orEmpty()
        LumeCard(modifier = Modifier.fillMaxWidth(), style = LumeCardStyle.Filled) {
            LumeStack(axis = LumeStackAxis.Horizontal, style = LumeStackStyle.Spread) {
                LumeStack(alignment = LumeStackAlignment.Start, spacing = { xSmall }) {
                    LumeText(text = specialty, weight = LumeTextWeight.Strong)
                    LumeText(
                        text = stringResource(Res.string.results_in, commune),
                        style = LumeTextStyle.TextSmall,
                        color = LumeTextColor.Secondary,
                    )
                }
                LumeButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.results_change)),
                    emphasis = LumeButtonEmphasis.Plain,
                    onClick = onBack,
                )
            }
        }
        CareLoadView(
            load,
            onRetry = { model.launch { search() } },
            offlineTitle = Res.string.results_offline_title,
            offlineBody = Res.string.results_offline_body,
        ) { content ->
            val filters = content.filters
            if (content.doctors.isEmpty()) {
                LumeEmptyState(
                    icon = LumeIcon.Stethoscope,
                    title = stringResource(Res.string.results_empty_title, specialty, commune),
                    message = stringResource(Res.string.results_empty_body),
                )
                if (content.nearby.isNotEmpty()) {
                    LumeList(
                        chrome = LumeListChrome.Open,
                        sections = listOf(
                            LumeListSection(
                                "nearby",
                                content.nearby,
                                title = stringResource(Res.string.results_nearby),
                            ),
                        ),
                        key = { it.commune.code },
                        scrolls = false,
                        onRowTap = { nearby -> model.launch { nearbyChosen(nearby.commune) } },
                    ) { nearby ->
                        LumeListRow(
                            title = nearby.commune.name,
                            description = pluralStringResource(
                                Res.plurals.results_nearby_row,
                                nearby.doctors,
                                nearby.doctors,
                                (relativeDayText(nearby.firstDay, content.today) ?: shortDateText(nearby.firstDay))
                                    .replaceFirstChar { it.lowercase() },
                            ),
                            disclosure = true,
                        )
                    }
                }
                return@CareLoadView
            }
            LumeStack(axis = LumeStackAxis.Horizontal, style = LumeStackStyle.Overflow, spacing = { small }) {
                val filtersTitle = if (filters.activeCount == 0) {
                    stringResource(Res.string.results_filters)
                } else {
                    stringResource(Res.string.results_filters_count, filters.activeCount)
                }
                LumeFilterChip(
                    title = filtersTitle,
                    isSelected = filters.activeCount > 0,
                    contentDescription = filtersTitle,
                    icon = LumeIcon.Sliders,
                    onClick = model::openFilters,
                )
                if (filters.sort != SearchSort.SOONEST) {
                    val label = stringResource(filters.sort.label)
                    LumeChip(
                        label,
                        stringResource(Res.string.results_remove_filter, label),
                        style = LumeChipStyle.Washed,
                    ) {
                        model.results.setFilters(filters.copy(sort = SearchSort.SOONEST))
                    }
                }
                if (filters.window != AvailabilityWindow.ANY) {
                    val label = stringResource(Res.string.window_week)
                    LumeChip(
                        label,
                        stringResource(Res.string.results_remove_filter, label),
                        style = LumeChipStyle.Washed,
                    ) {
                        model.results.setFilters(filters.copy(window = AvailabilityWindow.ANY))
                    }
                }
                if (filters.fourStarsUp) {
                    val label = stringResource(Res.string.filters_four_stars)
                    LumeChip(
                        label,
                        stringResource(Res.string.results_remove_filter, label),
                        style = LumeChipStyle.Washed,
                    ) {
                        model.results.setFilters(filters.copy(fourStarsUp = false))
                    }
                }
            }
            val shown = content.shown
            LumeText(
                text = pluralStringResource(Res.plurals.results_count, shown.size, shown.size) + " · " +
                    stringResource(filters.sort.caption),
                color = LumeTextColor.Secondary,
            )
            if (shown.isEmpty()) LumeCallout(text = stringResource(Res.string.results_filtered_empty))
            shown.forEach { entry ->
                DoctorResultCard(entry, content.today) { model.launch { schedule(entry) } }
            }
        }
    }
}

@Composable
private fun DoctorResultCard(entry: DirectoryDoctor, today: CalendarDate, onBook: () -> Unit) {
    LumeCard(modifier = Modifier.fillMaxWidth(), style = LumeCardStyle.Filled) {
        LumeStack(alignment = LumeStackAlignment.Fill, spacing = { medium }) {
            DoctorIdentity(entry.doctor, listOf(entry.doctor.specialty)) { DoctorRating(entry) }
            LumeDivider()
            LumeStack(axis = LumeStackAxis.Horizontal, style = LumeStackStyle.Spread) {
                LumeStack(alignment = LumeStackAlignment.Start, spacing = { xSmall }) {
                    LumeText(
                        text = stringResource(Res.string.results_next),
                        style = LumeTextStyle.Caption,
                        color = LumeTextColor.Secondary,
                    )
                    LumeText(
                        text = entry.nextSlot?.let {
                            slotText(it, today)
                        } ?: stringResource(Res.string.results_no_slot),
                        color = LumeTextColor.Accent,
                        weight = LumeTextWeight.Strong,
                    )
                }
                LumeButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.results_book)),
                    size = LumeButtonSize.Card,
                    state = if (entry.nextSlot != null) LumeFieldState.Enabled else LumeFieldState.Disabled,
                    onClick = onBook,
                )
            }
        }
    }
}

/** «★ 4,9 (128) · Providencia» as the kit's rating, or only the commune when nobody has rated yet. */
@Composable
internal fun DoctorRating(entry: DirectoryDoctor) {
    val rating = entry.rating
    if (rating == null) {
        LumeText(text = entry.commune, style = LumeTextStyle.TextSmall, color = LumeTextColor.Secondary)
        return
    }
    val score = rating.decimal()
    LumeRating(
        score = score,
        contentDescription = pluralStringResource(
            Res.plurals.rating_spoken,
            entry.ratingCount,
            score,
            entry.ratingCount,
            entry.commune,
        ),
        count = "(${entry.ratingCount})",
        trailing = "· ${entry.commune}",
    )
}

/** "4,9": one decimal, Chile's comma. The value is the server's; only its writing is the app's. */
private fun Double.decimal(): String {
    val tenths = kotlin.math.round(this * TENTHS).toInt()
    return "${tenths / TENTHS_INT},${tenths % TENTHS_INT}"
}

private const val TENTHS = 10.0
private const val TENTHS_INT = 10

/**
 * p4. PROVISIONAL deviations (ADR-0046): no «Más cerca» (the patient's location is not this app's to use,
 * constitution §1.0) and no «Previsión» (the contract has no coverage on a professional).
 */
@Composable
internal fun FiltersScreen(model: SearchViewModel, onBack: () -> Unit) {
    val draft by model.results.draft.collectAsState()
    val count = model.results.draftCount()
    PushedScreen(
        title = stringResource(Res.string.results_filters),
        onBack = onBack,
        action = {
            LumeStack(axis = LumeStackAxis.Horizontal, style = LumeStackStyle.Paired) {
                LumeButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.filters_clear)),
                    emphasis = LumeButtonEmphasis.OutlineNeutral,
                    size = LumeButtonSize.Bar,
                    width = LumeButtonWidth.Fill,
                    onClick = { model.results.draftChanged(SearchFilters()) },
                )
                LumeButton(
                    content = LumeButtonContent.Title(pluralStringResource(Res.plurals.filters_apply, count, count)),
                    size = LumeButtonSize.Bar,
                    width = LumeButtonWidth.Fill,
                    onClick = model::filtersApplied,
                )
            }
        },
    ) {
        LumeList(
            sections = listOf(
                LumeListSection("sort", SearchSort.entries, title = stringResource(Res.string.filters_sort)),
            ),
            key = { it },
            scrolls = false,
            selection = LumeListSelection.Single(draft.sort) { sort ->
                sort?.let { model.results.draftChanged(draft.copy(sort = it)) }
            },
            chrome = LumeListChrome.Open,
        ) { sort ->
            LumeListRow(title = stringResource(sort.label))
        }
        LumeEyebrow(stringResource(Res.string.filters_attention))
        val windowLabel = stringResource(draft.window.label)
        LumeList(
            items = listOf(Unit),
            key = { it },
            scrolls = false,
            onRowTap = { model.openAvailability() },
            chrome = LumeListChrome.Open,
        ) {
            LumeListRow(
                title = stringResource(Res.string.filters_availability),
                trailing = LumeListAccessory.Value(windowLabel),
                disclosure = true,
            )
        }
        val starsLabel = stringResource(Res.string.filters_four_stars)
        LumeList(items = listOf(Unit), key = { it }, scrolls = false, chrome = LumeListChrome.Open) {
            LumeListRow(
                title = starsLabel,
                trailing = LumeListAccessory.Toggle(
                    checked = draft.fourStarsUp,
                    onCheckedChange = { model.results.draftChanged(draft.copy(fourStarsUp = it)) },
                    contentDescription = starsLabel,
                ),
            )
        }
    }
}

private val AvailabilityWindow.label: StringResource
    get() = when (this) {
        AvailabilityWindow.ANY -> Res.string.window_any
        AvailabilityWindow.THIS_WEEK -> Res.string.window_week
    }

/** p4's «Disponibilidad», opened as its own list (spec p4): one choice, which returns to the filters. */
@Composable
internal fun AvailabilityScreen(model: SearchViewModel, onBack: () -> Unit) {
    val draft by model.results.draft.collectAsState()
    PushedScreen(title = stringResource(Res.string.filters_availability), onBack = onBack) {
        LumeList(
            items = AvailabilityWindow.entries,
            key = { it },
            scrolls = false,
            selection = LumeListSelection.Single(draft.window) { window -> window?.let(model::availabilityChosen) },
            chrome = LumeListChrome.Open,
        ) { window ->
            LumeListRow(title = stringResource(window.label))
        }
    }
}
