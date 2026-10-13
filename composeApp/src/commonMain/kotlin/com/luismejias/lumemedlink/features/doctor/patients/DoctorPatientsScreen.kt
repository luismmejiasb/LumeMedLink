package com.luismejias.lumemedlink.features.doctor.patients

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cl.lume.uicomposer.components.LumeCallout
import cl.lume.uicomposer.components.LumeCalloutStyle
import cl.lume.uicomposer.components.LumeCalloutSurface
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerScroll
import cl.lume.uicomposer.components.LumeList
import cl.lume.uicomposer.components.LumeListAccessory
import cl.lume.uicomposer.components.LumeListButtonLook
import cl.lume.uicomposer.components.LumeListChrome
import cl.lume.uicomposer.components.LumeListRow
import cl.lume.uicomposer.components.LumeListRowTextStyle
import cl.lume.uicomposer.components.LumeListSection
import cl.lume.uicomposer.components.LumeNavBar
import cl.lume.uicomposer.components.LumeNavBarTitleStyle
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTextStyle
import cl.lume.uicomposer.foundations.LumeIcon
import cl.lume.uicomposer.tokens.LumeTileTint
import com.luismejias.lumemedlink.core.care.PatientContact
import com.luismejias.lumemedlink.core.input.SensitiveSearchField
import com.luismejias.lumemedlink.core.screen.CareLoadView
import com.luismejias.lumemedlink.core.screen.TabBarClearance
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.common_search_clear
import com.luismejias.lumemedlink.resources.doctor_call_named
import com.luismejias.lumemedlink.resources.doctor_mail_named
import com.luismejias.lumemedlink.resources.doctor_patients_count
import com.luismejias.lumemedlink.resources.doctor_patients_none
import com.luismejias.lumemedlink.resources.doctor_patients_note
import com.luismejias.lumemedlink.resources.doctor_patients_search
import com.luismejias.lumemedlink.resources.doctor_tab_patients
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * m2 · Pacientes: the doctor's patients as contacts — a name, a phone, an address — never their record (§1.0).
 * The address opens the mail app with it and nothing else (ADR-0047). PROVISIONAL (ADR-0046): against a script; the
 * roster is a tenant-scoped route of trap T11.
 */
@Composable
internal fun DoctorPatientsScreen(model: DoctorPatientsViewModel, onCall: (String) -> Unit, onMail: (String) -> Unit) {
    val load by model.state.collectAsState()
    val standIn by model.standIn.collectAsState()
    LumeContainer(
        scroll = LumeContainerScroll.Scrollable,
        topBar = {
            LumeNavBar(title = stringResource(Res.string.doctor_tab_patients), titleStyle = LumeNavBarTitleStyle.Root)
        },
    ) {
        LumeStack(alignment = LumeStackAlignment.Start, spacing = { medium }) {
            CareLoadView(load, standIn, onRetry = model::refresh) { content ->
                SensitiveSearchField(
                    query = content.query,
                    onQueryChange = model::queryChanged,
                    prompt = stringResource(Res.string.doctor_patients_search),
                    clearLabel = stringResource(Res.string.common_search_clear),
                    modifier = Modifier.fillMaxWidth(),
                )
                LumeText(
                    text = pluralStringResource(
                        Res.plurals.doctor_patients_count,
                        content.roster.attendedCount,
                        content.roster.attendedCount,
                    ),
                    style = LumeTextStyle.TextSmall,
                    color = LumeTextColor.Secondary,
                )
                if (content.shown.isEmpty()) {
                    LumeCallout(text = stringResource(Res.string.doctor_patients_none))
                } else {
                    LumeList(
                        sections = content.sections.map { (letter, contacts) ->
                            LumeListSection(letter, contacts, title = letter)
                        },
                        key = { it.id },
                        scrolls = false,
                        background = Color.Transparent,
                        chrome = LumeListChrome.OpenIndex,
                    ) { contact -> ContactRow(contact, onCall, onMail) }
                }
                LumeCallout(
                    text = stringResource(Res.string.doctor_patients_note),
                    icon = LumeIcon.Lock,
                    style = LumeCalloutStyle.Info,
                    surface = LumeCalloutSurface.Washed,
                )
            }
            TabBarClearance()
        }
    }
}

@Composable
private fun ContactRow(contact: PatientContact, onCall: (String) -> Unit, onMail: (String) -> Unit) {
    val call = contact.mobile?.let { mobile ->
        LumeListAccessory.Button(
            LumeIcon.Phone,
            stringResource(Res.string.doctor_call_named, contact.displayName),
            LumeListButtonLook.Disc,
        ) { onCall(mobile.e164) }
    }
    val mail = contact.email?.let { email ->
        LumeListAccessory.Button(
            LumeIcon.Mail,
            stringResource(Res.string.doctor_mail_named, contact.displayName),
            LumeListButtonLook.Disc,
        ) { onMail(email.value) }
    }
    LumeListRow(
        title = contact.displayName,
        description = contact.mobile?.display,
        leading = LumeListAccessory.Avatar(contact.initials, LumeTileTint.deterministic(contact.id)),
        trailing = when {
            call != null && mail != null -> LumeListAccessory.ButtonPair(call, mail)
            else -> call ?: mail ?: LumeListAccessory.None
        },
        textStyle = LumeListRowTextStyle.Strong,
    )
}
