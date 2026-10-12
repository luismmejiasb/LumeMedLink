package com.luismejias.lumemedlink.features.patient.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cl.lume.uicomposer.components.LumeAvatar
import cl.lume.uicomposer.components.LumeAvatarSize
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerScroll
import cl.lume.uicomposer.components.LumeList
import cl.lume.uicomposer.components.LumeListAccessory
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
import cl.lume.uicomposer.components.LumeTextStyle
import cl.lume.uicomposer.foundations.LumeIcon
import cl.lume.uicomposer.tokens.LumeTileTint
import com.luismejias.lumemedlink.core.care.PatientProfile
import com.luismejias.lumemedlink.core.screen.CareLoadView
import com.luismejias.lumemedlink.core.screen.TabBarClearance
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.settings_complaints
import com.luismejias.lumemedlink.resources.settings_complaints_body
import com.luismejias.lumemedlink.resources.settings_correction
import com.luismejias.lumemedlink.resources.settings_edit_email
import com.luismejias.lumemedlink.resources.settings_edit_phone
import com.luismejias.lumemedlink.resources.settings_email
import com.luismejias.lumemedlink.resources.settings_phone
import com.luismejias.lumemedlink.resources.settings_ratings
import com.luismejias.lumemedlink.resources.settings_ratings_sent
import com.luismejias.lumemedlink.resources.settings_reminders
import com.luismejias.lumemedlink.resources.settings_reminders_none
import com.luismejias.lumemedlink.resources.settings_reminders_one
import com.luismejias.lumemedlink.resources.settings_reminders_two
import com.luismejias.lumemedlink.resources.settings_rut
import com.luismejias.lumemedlink.resources.settings_sign_out
import com.luismejias.lumemedlink.resources.settings_title
import com.luismejias.lumemedlink.resources.settings_verified
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The rows under the profile, in the design's order. */
internal enum class SettingsEntry { CORRECTION, REMINDERS, RATINGS, COMPLAINTS, SIGN_OUT }

/**
 * Configuración (p7). The RUT is never edited here — it is the identifier (the handoff's inherited rule); a
 * wrong one goes through "Solicitar corrección de un dato", which the doctor or their secretary approves.
 * PROVISIONAL (ADR-0046): the camera badge on the avatar waits for the photo flow (k1–k4).
 */
@Composable
internal fun SettingsScreen(
    model: SettingsViewModel,
    onEditPhone: () -> Unit,
    onEditEmail: () -> Unit,
    onEntry: (SettingsEntry) -> Unit,
) {
    val load by model.state.collectAsState()
    CareLoadView(load, onRetry = model::refresh, standalone = true) { content ->
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
                    LumeAvatar(
                        initials = profile.initials,
                        size = LumeAvatarSize.Large,
                        contentDescription = "${profile.givenNames} ${profile.familyNames}",
                    )
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
                    items = listOf(SettingsEntry.REMINDERS, SettingsEntry.RATINGS, SettingsEntry.COMPLAINTS),
                    key = { it },
                    scrolls = false,
                    onRowTap = onEntry,
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
                    items = listOf(SettingsEntry.SIGN_OUT),
                    key = { it },
                    scrolls = false,
                    onRowTap = onEntry,
                    chrome = LumeListChrome.OpenRuled,
                ) {
                    LumeListRow(
                        title = stringResource(Res.string.settings_sign_out),
                        role = LumeListRowRole.Destructive,
                    )
                }
                TabBarClearance()
            }
        }
    }
}

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
                    onClick = onEditEmail,
                ),
                textStyle = LumeListRowTextStyle.LabelOverValue,
            )
        }
    }
}
