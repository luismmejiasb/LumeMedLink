package com.luismejias.lumemedlink.features.patient.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cl.lume.uicomposer.components.LumeAlert
import cl.lume.uicomposer.components.LumeAlertAction
import cl.lume.uicomposer.components.LumeButtonRole
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerScroll
import cl.lume.uicomposer.components.LumeList
import cl.lume.uicomposer.components.LumeListAccessory
import cl.lume.uicomposer.components.LumeListButtonLook
import cl.lume.uicomposer.components.LumeListChrome
import cl.lume.uicomposer.components.LumeListRow
import cl.lume.uicomposer.components.LumeListRowRole
import cl.lume.uicomposer.components.LumeListRowTextStyle
import cl.lume.uicomposer.components.LumeNavBar
import cl.lume.uicomposer.components.LumeNavBarTitleStyle
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeTag
import cl.lume.uicomposer.components.LumeTagStyle
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextAlignment
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTextStyle
import cl.lume.uicomposer.foundations.LumeIcon
import cl.lume.uicomposer.tokens.LumeStatusStyle
import cl.lume.uicomposer.tokens.LumeTileTint
import com.luismejias.lumemedlink.core.care.PatientProfile
import com.luismejias.lumemedlink.core.device.rememberAppVersion
import com.luismejias.lumemedlink.core.screen.CareLoadView
import com.luismejias.lumemedlink.core.screen.ProfileAvatar
import com.luismejias.lumemedlink.core.screen.ProfilePhotoModel
import com.luismejias.lumemedlink.core.screen.TabBarClearance
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.common_cancel
import com.luismejias.lumemedlink.resources.settings_appearance
import com.luismejias.lumemedlink.resources.settings_complaints
import com.luismejias.lumemedlink.resources.settings_complaints_body
import com.luismejias.lumemedlink.resources.settings_copyright
import com.luismejias.lumemedlink.resources.settings_correction
import com.luismejias.lumemedlink.resources.settings_edit_email
import com.luismejias.lumemedlink.resources.settings_edit_phone
import com.luismejias.lumemedlink.resources.settings_email
import com.luismejias.lumemedlink.resources.settings_language
import com.luismejias.lumemedlink.resources.settings_licenses
import com.luismejias.lumemedlink.resources.settings_phone
import com.luismejias.lumemedlink.resources.settings_privacy
import com.luismejias.lumemedlink.resources.settings_privacy_body
import com.luismejias.lumemedlink.resources.settings_ratings
import com.luismejias.lumemedlink.resources.settings_ratings_sent
import com.luismejias.lumemedlink.resources.settings_reminders
import com.luismejias.lumemedlink.resources.settings_reminders_none
import com.luismejias.lumemedlink.resources.settings_reminders_one
import com.luismejias.lumemedlink.resources.settings_reminders_two
import com.luismejias.lumemedlink.resources.settings_rut
import com.luismejias.lumemedlink.resources.settings_sign_out
import com.luismejias.lumemedlink.resources.settings_terms
import com.luismejias.lumemedlink.resources.settings_title
import com.luismejias.lumemedlink.resources.settings_verified
import com.luismejias.lumemedlink.resources.settings_version
import com.luismejias.lumemedlink.resources.sign_out_body
import com.luismejias.lumemedlink.resources.sign_out_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The rows under the profile, in the design's order. */
internal enum class SettingsEntry {
    CORRECTION,
    REMINDERS,
    RATINGS,
    COMPLAINTS,
    APPEARANCE,
    LANGUAGE,
    PRIVACY,
    TERMS,
    LICENSES,
    SIGN_OUT,
}

/**
 * Configuración (p7). The RUT is never edited here — it is the identifier (the handoff's inherited rule); a
 * wrong one goes through "Solicitar corrección de un dato", which the doctor or their secretary approves.
 * The avatar's camera button opens the photo's flow (k1–k4), which the host draws over the tabs.
 *
 * Under the design's rows, the author's of 2026-10-13: the theme and the language, the legal pages (the telemetry
 * consent now lives inside Privacidad), «Cerrar sesión» behind a confirmation, and the copyright and the installed
 * version at the very bottom. A row that pushes a page opens it in [model]; [onEntry] hears the rest.
 */
@Composable
internal fun SettingsScreen(
    model: SettingsViewModel,
    photo: ProfilePhotoModel,
    onEditPhone: () -> Unit,
    onEditEmail: () -> Unit,
    onEntry: (SettingsEntry) -> Unit,
) {
    val load by model.state.collectAsState()
    val standIn by model.standIn.collectAsState()
    val theme by model.theme.collectAsState()
    val language by model.language.collectAsState()
    val confirmingSignOut by model.signOutConfirming.collectAsState()
    val version = rememberAppVersion()
    val openOrTell: (SettingsEntry) -> Unit = { entry ->
        when (entry) {
            SettingsEntry.APPEARANCE -> model.open(SettingsRoute.APPEARANCE)
            SettingsEntry.LANGUAGE -> model.open(SettingsRoute.LANGUAGE)
            SettingsEntry.PRIVACY -> model.open(SettingsRoute.PRIVACY)
            SettingsEntry.TERMS -> model.open(SettingsRoute.TERMS)
            SettingsEntry.LICENSES -> model.open(SettingsRoute.LICENSES)
            SettingsEntry.SIGN_OUT -> model.askSignOut()
            else -> onEntry(entry)
        }
    }
    CareLoadView(load, standIn, onRetry = model::refresh, standalone = true) { content ->
        val profile = content.profile
        LumeContainer(
            scroll = LumeContainerScroll.Scrollable,
            topBar = {
                LumeNavBar(title = stringResource(Res.string.settings_title), titleStyle = LumeNavBarTitleStyle.Root)
            },
        ) {
            LumeStack(alignment = LumeStackAlignment.Start, spacing = { large }) {
                LumeStack(
                    modifier = Modifier.fillMaxWidth(),
                    alignment = LumeStackAlignment.Center,
                    spacing = { small },
                ) {
                    ProfileAvatar(photo, profile.initials, "${profile.givenNames} ${profile.familyNames}")
                    LumeText(text = "${profile.givenNames} ${profile.familyNames}", style = LumeTextStyle.HeadingMedium)
                    if (profile.verified) {
                        LumeTag(text = stringResource(Res.string.settings_verified), style = LumeTagStyle.Success)
                    }
                }
                ProfileFields(profile, onEditPhone, onEditEmail)
                LumeList(
                    items = listOf(SettingsEntry.CORRECTION),
                    key = { it },
                    scrolls = false,
                    onRowTap = onEntry,
                    chrome = LumeListChrome.OpenRuled,
                ) {
                    LumeListRow(
                        textStyle = LumeListRowTextStyle.Strong,
                        title = stringResource(Res.string.settings_correction),
                        leading = LumeListAccessory.Tile(LumeIcon.Flag, LumeTileTint.Amber),
                        disclosure = true,
                    )
                }
                LumeList(
                    items = listOf(
                        SettingsEntry.REMINDERS,
                        SettingsEntry.RATINGS,
                        SettingsEntry.COMPLAINTS,
                    ),
                    key = { it },
                    scrolls = false,
                    onRowTap = openOrTell,
                    chrome = LumeListChrome.OpenRuled,
                ) { entry ->
                    when (entry) {
                        SettingsEntry.REMINDERS -> LumeListRow(
                            textStyle = LumeListRowTextStyle.Strong,
                            title = stringResource(Res.string.settings_reminders),
                            description = remindersText(content.remindersHoursBefore),
                            leading = LumeListAccessory.Tile(LumeIcon.Bell, LumeTileTint.Violet),
                            disclosure = true,
                        )
                        SettingsEntry.RATINGS -> LumeListRow(
                            textStyle = LumeListRowTextStyle.Strong,
                            title = stringResource(Res.string.settings_ratings),
                            description = pluralStringResource(
                                Res.plurals.settings_ratings_sent,
                                content.sentRatings,
                                content.sentRatings,
                            ),
                            leading = LumeListAccessory.Tile(LumeIcon.Star, LumeTileTint.Gold),
                            disclosure = true,
                        )
                        else -> LumeListRow(
                            textStyle = LumeListRowTextStyle.Strong,
                            title = stringResource(Res.string.settings_complaints),
                            description = stringResource(Res.string.settings_complaints_body),
                            leading = LumeListAccessory.Tile(LumeIcon.Flag, LumeTileTint.Rose),
                            disclosure = true,
                        )
                    }
                }
                LumeList(
                    items = listOf(SettingsEntry.APPEARANCE, SettingsEntry.LANGUAGE),
                    key = { it },
                    scrolls = false,
                    onRowTap = openOrTell,
                    chrome = LumeListChrome.OpenRuled,
                ) { entry ->
                    if (entry == SettingsEntry.APPEARANCE) {
                        LumeListRow(
                            textStyle = LumeListRowTextStyle.Strong,
                            title = stringResource(Res.string.settings_appearance),
                            leading = LumeListAccessory.Tile(LumeIcon.Sliders, LumeTileTint.Iris),
                            trailing = LumeListAccessory.Value(stringResource(themeLabel(theme))),
                            disclosure = true,
                        )
                    } else {
                        LumeListRow(
                            textStyle = LumeListRowTextStyle.Strong,
                            title = stringResource(Res.string.settings_language),
                            leading = LumeListAccessory.Tile(LumeIcon.Globe, LumeTileTint.Sky),
                            trailing = LumeListAccessory.Value(stringResource(languageLabel(language))),
                            disclosure = true,
                        )
                    }
                }
                LumeList(
                    items = listOf(SettingsEntry.PRIVACY, SettingsEntry.TERMS, SettingsEntry.LICENSES),
                    key = { it },
                    scrolls = false,
                    onRowTap = openOrTell,
                    chrome = LumeListChrome.OpenRuled,
                ) { entry ->
                    when (entry) {
                        SettingsEntry.PRIVACY -> LumeListRow(
                            textStyle = LumeListRowTextStyle.Strong,
                            title = stringResource(Res.string.settings_privacy),
                            description = stringResource(Res.string.settings_privacy_body),
                            leading = LumeListAccessory.Tile(LumeIcon.Shield, LumeTileTint.Teal),
                            disclosure = true,
                        )
                        SettingsEntry.TERMS -> LumeListRow(
                            textStyle = LumeListRowTextStyle.Strong,
                            title = stringResource(Res.string.settings_terms),
                            leading = LumeListAccessory.Tile(LumeIcon.Doc, LumeTileTint.Slate),
                            disclosure = true,
                        )
                        else -> LumeListRow(
                            textStyle = LumeListRowTextStyle.Strong,
                            title = stringResource(Res.string.settings_licenses),
                            leading = LumeListAccessory.Tile(LumeIcon.Clip, LumeTileTint.Slate),
                            disclosure = true,
                        )
                    }
                }
                LumeList(
                    items = listOf(SettingsEntry.SIGN_OUT),
                    key = { it },
                    scrolls = false,
                    onRowTap = openOrTell,
                    chrome = LumeListChrome.OpenRuled,
                ) {
                    LumeListRow(
                        title = stringResource(Res.string.settings_sign_out),
                        role = LumeListRowRole.Destructive,
                    )
                }
                LumeStack(
                    modifier = Modifier.fillMaxWidth(),
                    alignment = LumeStackAlignment.Center,
                    spacing = { xSmall },
                ) {
                    LumeText(
                        text = stringResource(Res.string.settings_copyright, COPYRIGHT_YEAR),
                        style = LumeTextStyle.Caption,
                        color = LumeTextColor.Secondary,
                        alignment = LumeTextAlignment.Center,
                    )
                    LumeText(
                        text = stringResource(Res.string.settings_version, version.name, version.build),
                        style = LumeTextStyle.Caption,
                        color = LumeTextColor.Secondary,
                        alignment = LumeTextAlignment.Center,
                    )
                }
                TabBarClearance()
            }
        }
    }
    LumeAlert(
        visible = confirmingSignOut,
        title = stringResource(Res.string.sign_out_title),
        message = stringResource(Res.string.sign_out_body),
        style = LumeStatusStyle.Warning,
        confirm = LumeAlertAction(
            title = stringResource(Res.string.settings_sign_out),
            role = LumeButtonRole.Destructive,
        ) {
            model.signOutAnswered()
            onEntry(SettingsEntry.SIGN_OUT)
        },
        cancel = LumeAlertAction(title = stringResource(Res.string.common_cancel), onSelect = model::signOutAnswered),
    )
}

/** The year of first publication, as a copyright notice states it — not today's: it never moves with the clock. */
private const val COPYRIGHT_YEAR = "2026"

@Composable
private fun remindersText(hours: List<Int>): String = when (hours.size) {
    0 -> stringResource(Res.string.settings_reminders_none)
    1 -> stringResource(Res.string.settings_reminders_one, hours[0])
    else -> stringResource(Res.string.settings_reminders_two, hours[0], hours[1])
}

private enum class ProfileField { RUT, PHONE, EMAIL }

/** p7's identity card: each datum's name small over its value, the RUT locked, the contact pencils to edit. */
@Composable
private fun ProfileFields(profile: PatientProfile, onEditPhone: () -> Unit, onEditEmail: () -> Unit) {
    LumeList(items = ProfileField.entries, key = { it }, scrolls = false, chrome = LumeListChrome.OpenRuled) { field ->
        when (field) {
            ProfileField.RUT -> LumeListRow(
                title = stringResource(Res.string.settings_rut),
                description = profile.rut.display,
                trailing = LumeListAccessory.Icon(LumeIcon.Lock, ""),
                textStyle = LumeListRowTextStyle.LabelOverValue,
            )
            ProfileField.PHONE -> LumeListRow(
                title = stringResource(Res.string.settings_phone),
                description = profile.mobile.display,
                trailing = LumeListAccessory.Button(
                    LumeIcon.Edit,
                    stringResource(Res.string.settings_edit_phone),
                    LumeListButtonLook.Accent,
                    onClick = onEditPhone,
                ),
                textStyle = LumeListRowTextStyle.LabelOverValue,
            )
            ProfileField.EMAIL -> LumeListRow(
                title = stringResource(Res.string.settings_email),
                description = profile.email.value,
                trailing = LumeListAccessory.Button(
                    LumeIcon.Edit,
                    stringResource(Res.string.settings_edit_email),
                    LumeListButtonLook.Accent,
                    onClick = onEditEmail,
                ),
                textStyle = LumeListRowTextStyle.LabelOverValue,
            )
        }
    }
}
