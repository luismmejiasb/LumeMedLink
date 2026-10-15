package com.luismejias.lumemedlink.features.patient.doctors

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cl.lume.uicomposer.components.LumeButton
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeButtonEmphasis
import cl.lume.uicomposer.components.LumeButtonSize
import cl.lume.uicomposer.components.LumeCallout
import cl.lume.uicomposer.components.LumeCalloutStyle
import cl.lume.uicomposer.components.LumeCalloutSurface
import cl.lume.uicomposer.components.LumeCard
import cl.lume.uicomposer.components.LumeCardStyle
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerScroll
import cl.lume.uicomposer.components.LumeDivider
import cl.lume.uicomposer.components.LumeEmptyState
import cl.lume.uicomposer.components.LumeEmptyStateLook
import cl.lume.uicomposer.components.LumeList
import cl.lume.uicomposer.components.LumeListRow
import cl.lume.uicomposer.components.LumeListRowTextStyle
import cl.lume.uicomposer.components.LumeListStyle
import cl.lume.uicomposer.components.LumeNavBar
import cl.lume.uicomposer.components.LumeNavBarTitleStyle
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeStackAxis
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.core.care.MyDoctor
import com.luismejias.lumemedlink.core.input.SensitiveSearchField
import com.luismejias.lumemedlink.core.screen.CareLoadView
import com.luismejias.lumemedlink.core.screen.TabBarClearance
import com.luismejias.lumemedlink.core.screen.careRefresh
import com.luismejias.lumemedlink.core.screen.dayMonthYearText
import com.luismejias.lumemedlink.features.patient.parts.doctorMarker
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.common_search_clear
import com.luismejias.lumemedlink.resources.doctors_attentions
import com.luismejias.lumemedlink.resources.doctors_call
import com.luismejias.lumemedlink.resources.doctors_empty_body
import com.luismejias.lumemedlink.resources.doctors_empty_title
import com.luismejias.lumemedlink.resources.doctors_footer
import com.luismejias.lumemedlink.resources.doctors_mail
import com.luismejias.lumemedlink.resources.doctors_no_match
import com.luismejias.lumemedlink.resources.doctors_private
import com.luismejias.lumemedlink.resources.doctors_search_prompt
import com.luismejias.lumemedlink.resources.last_visit
import com.luismejias.lumemedlink.resources.search_doctor
import com.luismejias.lumemedlink.resources.tab_doctors
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Mis médicos (p6, e9). The filter is a field of the hardened primitive (ADR-0013), not the kit's search
 * field: the search field takes no text case, and a typed name must not enter the keyboard's dictionary.
 * Calling hands the number to the phone's dialer — what the doctor chose to share, nothing more. PROVISIONAL
 * (ADR-0046): the design's "Correo" button waits for the author (ADR-0007 closes the mail intent on Android), so
 * the address is shown, not acted on. Read-only (the author, 2026-10-15): a doctor opens nothing, so no row shows a
 * disclosure; what a doctor's detail could hold is for after the MVP.
 */
@Composable
internal fun MyDoctorsScreen(
    model: MyDoctorsViewModel,
    onCall: (String) -> Unit,
    onMail: (String) -> Unit,
    /** Null while the directory is off (ADR-0060): the empty state offers no search. */
    onSearch: (() -> Unit)?,
) {
    val load by model.state.collectAsState()
    val standIn by model.standIn.collectAsState()
    CareLoadView(load, standIn, onRetry = model::refresh, standalone = true) { content ->
        LumeContainer(
            scroll = LumeContainerScroll.Scrollable,
            refresh = careRefresh(model.pull, model::pullToRefresh),
            topBar = {
                LumeNavBar(title = stringResource(Res.string.tab_doctors), titleStyle = LumeNavBarTitleStyle.Root)
            },
        ) {
            LumeStack(alignment = LumeStackAlignment.Start, spacing = { medium }) {
                if (content.all.isEmpty()) {
                    LumeEmptyState(
                        look = LumeEmptyStateLook.Tiled(),
                        icon = LumeIcon.PersonFill,
                        title = stringResource(Res.string.doctors_empty_title),
                        message = stringResource(Res.string.doctors_empty_body),
                        action = onSearch?.let { LumeButtonContent.Title(stringResource(Res.string.search_doctor)) },
                        onAction = onSearch,
                    )
                } else {
                    SensitiveSearchField(
                        query = content.query,
                        onQueryChange = model::queryChanged,
                        prompt = stringResource(Res.string.doctors_search_prompt),
                        clearLabel = stringResource(Res.string.common_search_clear),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Under the search, as a grey note (the author, 2026-10-15), not at the foot of the list.
                    LumeCallout(
                        text = stringResource(Res.string.doctors_footer),
                        style = LumeCalloutStyle.Neutral,
                        surface = LumeCalloutSurface.Washed,
                    )
                    if (content.shown.isEmpty()) {
                        LumeCallout(text = stringResource(Res.string.doctors_no_match))
                    }
                    if (content.shown.isNotEmpty()) {
                        // p6 draws every doctor in one card, a line between each.
                        LumeCard(modifier = Modifier.fillMaxWidth(), style = LumeCardStyle.Filled) {
                            LumeStack(alignment = LumeStackAlignment.Fill, spacing = { medium }) {
                                content.shown.forEachIndexed { index, entry ->
                                    if (index > 0) LumeDivider()
                                    DoctorEntry(entry, onCall, onMail)
                                }
                            }
                        }
                    }
                }
                TabBarClearance()
            }
        }
    }
}

@Composable
private fun DoctorEntry(entry: MyDoctor, onCall: (String) -> Unit, onMail: (String) -> Unit) {
    LumeStack(alignment = LumeStackAlignment.Start, spacing = { small }) {
        LumeList(
            items = listOf(entry),
            key = { it.doctor.id },
            style = LumeListStyle.Embedded,
        ) {
            LumeListRow(
                textStyle = LumeListRowTextStyle.Strong,
                title = it.doctor.displayName,
                description = listOfNotNull(
                    listOfNotNull(
                        it.doctor.specialty,
                        it.attentions?.let { n -> pluralStringResource(Res.plurals.doctors_attentions, n, n) },
                    ).joinToString(" · "),
                    it.lastVisit?.let { day -> stringResource(Res.string.last_visit, dayMonthYearText(day)) },
                ).joinToString("\n"),
                leading = doctorMarker(it.doctor),
            )
        }
        if (entry.sharesContact) {
            LumeStack(axis = LumeStackAxis.Horizontal, spacing = { small }) {
                entry.phone?.let { phone ->
                    LumeButton(
                        content = LumeButtonContent.LeadingIcon(
                            LumeIcon.Phone,
                            stringResource(Res.string.doctors_call),
                        ),
                        emphasis = LumeButtonEmphasis.OutlineNeutral,
                        size = LumeButtonSize.Card,
                        onClick = { onCall(phone) },
                    )
                }
                entry.email?.let { email ->
                    LumeButton(
                        content = LumeButtonContent.LeadingIcon(
                            LumeIcon.Mail,
                            stringResource(Res.string.doctors_mail),
                        ),
                        emphasis = LumeButtonEmphasis.OutlineNeutral,
                        size = LumeButtonSize.Card,
                        onClick = { onMail(email) },
                    )
                }
            }
        } else {
            LumeCallout(text = stringResource(Res.string.doctors_private), icon = LumeIcon.Lock)
        }
    }
}
