package com.luismejias.lumemedlink.features.patient.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cl.lume.uicomposer.components.LumeList
import cl.lume.uicomposer.components.LumeListAccessory
import cl.lume.uicomposer.components.LumeListChrome
import cl.lume.uicomposer.components.LumeListRow
import cl.lume.uicomposer.components.LumeListRowTextStyle
import cl.lume.uicomposer.components.LumeListSelection
import cl.lume.uicomposer.components.LumeTag
import cl.lume.uicomposer.components.LumeTagStyle
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTextStyle
import com.luismejias.lumemedlink.core.appearance.LanguageChoice
import com.luismejias.lumemedlink.core.appearance.ThemeChoice
import com.luismejias.lumemedlink.features.patient.parts.PushedScreen
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.language_english
import com.luismejias.lumemedlink.resources.language_footnote
import com.luismejias.lumemedlink.resources.language_spanish
import com.luismejias.lumemedlink.resources.language_system
import com.luismejias.lumemedlink.resources.legal_draft
import com.luismejias.lumemedlink.resources.licenses_intro
import com.luismejias.lumemedlink.resources.privacy_body
import com.luismejias.lumemedlink.resources.privacy_heading
import com.luismejias.lumemedlink.resources.privacy_telemetry_body
import com.luismejias.lumemedlink.resources.privacy_telemetry_heading
import com.luismejias.lumemedlink.resources.settings_appearance
import com.luismejias.lumemedlink.resources.settings_language
import com.luismejias.lumemedlink.resources.settings_licenses
import com.luismejias.lumemedlink.resources.settings_privacy
import com.luismejias.lumemedlink.resources.settings_telemetry
import com.luismejias.lumemedlink.resources.settings_terms
import com.luismejias.lumemedlink.resources.terms_body
import com.luismejias.lumemedlink.resources.theme_dark
import com.luismejias.lumemedlink.resources.theme_footnote
import com.luismejias.lumemedlink.resources.theme_light
import com.luismejias.lumemedlink.resources.theme_system
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The page [route] names, pushed over Configuración; its back pops it. */
@Composable
internal fun SettingsPage(route: SettingsRoute, model: SettingsViewModel) {
    when (route) {
        SettingsRoute.APPEARANCE -> {
            val theme by model.theme.collectAsState()
            ChoicePage(
                title = stringResource(Res.string.settings_appearance),
                choices = ThemeChoice.entries,
                chosen = theme,
                label = ::themeLabel,
                footnote = stringResource(Res.string.theme_footnote),
                onChoose = model::themeChosen,
                onBack = model::back,
            )
        }
        SettingsRoute.LANGUAGE -> {
            val language by model.language.collectAsState()
            ChoicePage(
                title = stringResource(Res.string.settings_language),
                choices = LanguageChoice.entries,
                chosen = language,
                label = ::languageLabel,
                footnote = stringResource(Res.string.language_footnote),
                onChoose = model::languageChosen,
                onBack = model::back,
            )
        }
        SettingsRoute.PRIVACY -> PrivacyPage(model)
        SettingsRoute.TERMS -> PushedScreen(title = stringResource(Res.string.settings_terms), onBack = model::back) {
            LegalDraftTag()
            LumeText(stringResource(Res.string.terms_body))
        }
        SettingsRoute.LICENSES -> LicensesPage(model)
    }
}

internal fun themeLabel(choice: ThemeChoice): StringResource = when (choice) {
    ThemeChoice.SYSTEM -> Res.string.theme_system
    ThemeChoice.LIGHT -> Res.string.theme_light
    ThemeChoice.DARK -> Res.string.theme_dark
}

internal fun languageLabel(choice: LanguageChoice): StringResource = when (choice) {
    LanguageChoice.SYSTEM -> Res.string.language_system
    LanguageChoice.SPANISH -> Res.string.language_spanish
    LanguageChoice.ENGLISH -> Res.string.language_english
}

/** One of a few, the chosen one checked — the iPhone's own settings page; choosing it again keeps it. */
@Composable
private fun <T : Any> ChoicePage(
    title: String,
    choices: List<T>,
    chosen: T,
    label: (T) -> StringResource,
    footnote: String,
    onChoose: (T) -> Unit,
    onBack: () -> Unit,
) {
    PushedScreen(title = title, onBack = onBack) {
        LumeList(
            items = choices,
            key = { it },
            selection = LumeListSelection.Single(chosen) { picked -> picked?.let(onChoose) },
            scrolls = false,
            chrome = LumeListChrome.OpenRuled,
        ) { LumeListRow(title = stringResource(label(it))) }
        LumeText(text = footnote, style = LumeTextStyle.TextSmall, color = LumeTextColor.Secondary)
    }
}

/**
 * Privacidad: what the app keeps, and the consent of ADR-0052 §2, withdrawn or given here — the author's call of
 * 2026-10-13 moved it from Configuración's root into this page. The text is PROVISIONAL until the lawyer's.
 */
@Composable
private fun PrivacyPage(model: SettingsViewModel) {
    val telemetry by model.telemetry.collectAsState()
    PushedScreen(title = stringResource(Res.string.settings_privacy), onBack = model::back) {
        LegalDraftTag()
        LumeText(stringResource(Res.string.privacy_heading), style = LumeTextStyle.HeadingSmall)
        LumeText(stringResource(Res.string.privacy_body))
        LumeText(stringResource(Res.string.privacy_telemetry_heading), style = LumeTextStyle.HeadingSmall)
        LumeText(stringResource(Res.string.privacy_telemetry_body))
        val title = stringResource(Res.string.settings_telemetry)
        LumeList(items = listOf(Unit), key = { it }, scrolls = false, chrome = LumeListChrome.OpenRuled) {
            LumeListRow(
                textStyle = LumeListRowTextStyle.Strong,
                title = title,
                trailing = LumeListAccessory.Toggle(
                    checked = telemetry,
                    onCheckedChange = model::telemetryChanged,
                    contentDescription = title,
                ),
            )
        }
    }
}

/** Licencias: the third parties the app ships, generated from the lockfiles (Scripts/generate-licenses.py). */
@Composable
private fun LicensesPage(model: SettingsViewModel) {
    val licenses by model.licenses.collectAsState()
    PushedScreen(title = stringResource(Res.string.settings_licenses), onBack = model::back) {
        LumeText(stringResource(Res.string.licenses_intro), color = LumeTextColor.Secondary)
        LumeList(items = licenses, key = { it.project }, scrolls = false, chrome = LumeListChrome.OpenRuled) {
            LumeListRow(title = it.project, description = it.license)
        }
    }
}

/** The legal pages say they are a draft until the lawyer's text replaces them (the author, 2026-10-13). */
@Composable
private fun LegalDraftTag() {
    LumeTag(text = stringResource(Res.string.legal_draft), style = LumeTagStyle.Warning)
}
