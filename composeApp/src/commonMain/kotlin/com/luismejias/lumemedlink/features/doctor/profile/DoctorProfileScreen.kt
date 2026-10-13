package com.luismejias.lumemedlink.features.doctor.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerScroll
import cl.lume.uicomposer.components.LumeList
import cl.lume.uicomposer.components.LumeListAccessory
import cl.lume.uicomposer.components.LumeListButtonLook
import cl.lume.uicomposer.components.LumeListChrome
import cl.lume.uicomposer.components.LumeListRow
import cl.lume.uicomposer.components.LumeListRowRole
import cl.lume.uicomposer.components.LumeListRowTextStyle
import cl.lume.uicomposer.components.LumeListTileEmphasis
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeTag
import cl.lume.uicomposer.components.LumeTagStyle
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextAlignment
import cl.lume.uicomposer.components.LumeTextStyle
import cl.lume.uicomposer.foundations.LumeIcon
import cl.lume.uicomposer.tokens.LumeTileTint
import com.luismejias.lumemedlink.core.care.DoctorProfile
import com.luismejias.lumemedlink.core.screen.CareLoadView
import com.luismejias.lumemedlink.core.screen.ProfileAvatar
import com.luismejias.lumemedlink.core.screen.ProfilePhotoModel
import com.luismejias.lumemedlink.core.screen.TabBarClearance
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.doctor_address
import com.luismejias.lumemedlink.resources.doctor_corrections
import com.luismejias.lumemedlink.resources.doctor_corrections_body
import com.luismejias.lumemedlink.resources.doctor_edit_named
import com.luismejias.lumemedlink.resources.doctor_linked
import com.luismejias.lumemedlink.resources.doctor_phone
import com.luismejias.lumemedlink.resources.doctor_ratings
import com.luismejias.lumemedlink.resources.doctor_ratings_body
import com.luismejias.lumemedlink.resources.doctor_share
import com.luismejias.lumemedlink.resources.doctor_share_body
import com.luismejias.lumemedlink.resources.doctor_specialty
import com.luismejias.lumemedlink.resources.settings_sign_out
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private enum class ProfileField { SPECIALTY, ADDRESS, PHONE }

private enum class ProfileEntry { RATINGS, CORRECTIONS }

/**
 * m3 · the doctor's public profile: what patients see in Buscar and in their appointments, and the one switch that
 * shares their contact with all of them. PROVISIONAL (ADR-0046): the pencils, the ratings and the corrections open
 * nothing yet; the camera button opens the photo's flow (k1–k4). «Abrir LumeMed» is not here (the author's call).
 */
@Composable
internal fun DoctorProfileScreen(model: DoctorProfileViewModel, photo: ProfilePhotoModel, onSignOut: () -> Unit) {
    val load by model.state.collectAsState()
    val standIn by model.standIn.collectAsState()
    LumeContainer(scroll = LumeContainerScroll.Scrollable) {
        LumeStack(alignment = LumeStackAlignment.Start, spacing = { large }) {
            CareLoadView(load, standIn, onRetry = model::refresh) { profile ->
                ProfileHeader(profile, photo)
                ProfileFields(profile)
                ShareRow(profile, onShare = { share -> model.launch { shareContact(share) } })
                ProfileEntries(profile)
                SignOut(onSignOut)
            }
            TabBarClearance()
        }
    }
}

@Composable
private fun ProfileHeader(profile: DoctorProfile, photo: ProfilePhotoModel) {
    LumeStack(modifier = Modifier.fillMaxWidth(), alignment = LumeStackAlignment.Center, spacing = { small }) {
        ProfileAvatar(
            photo,
            profile.doctor.initials,
            profile.doctor.displayName,
            tint = LumeTileTint.deterministic(profile.doctor.id),
        )
        LumeText(
            text = profile.doctor.displayName,
            style = LumeTextStyle.HeadingMedium,
            alignment = LumeTextAlignment.Center,
        )
        if (profile.linkedToLumeMed) {
            LumeTag(text = stringResource(Res.string.doctor_linked), style = LumeTagStyle.Success)
        }
    }
}

@Composable
private fun ProfileFields(profile: DoctorProfile) {
    val labels = mapOf(
        ProfileField.SPECIALTY to stringResource(Res.string.doctor_specialty),
        ProfileField.ADDRESS to stringResource(Res.string.doctor_address),
        ProfileField.PHONE to stringResource(Res.string.doctor_phone),
    )
    val edits = labels.mapValues { (_, label) -> stringResource(Res.string.doctor_edit_named, label.lowercase()) }
    LumeList(
        items = ProfileField.entries,
        key = { it },
        scrolls = false,
        background = Color.Transparent,
        chrome = LumeListChrome.OpenRuled,
    ) { field ->
        LumeListRow(
            title = labels.getValue(field),
            description = when (field) {
                ProfileField.SPECIALTY -> profile.doctor.specialty
                ProfileField.ADDRESS -> profile.address
                ProfileField.PHONE -> profile.phone
            },
            trailing = LumeListAccessory.Button(LumeIcon.Edit, edits.getValue(field), LumeListButtonLook.Accent) {},
            textStyle = LumeListRowTextStyle.LabelOverValue,
        )
    }
}

@Composable
private fun ShareRow(profile: DoctorProfile, onShare: (Boolean) -> Unit) {
    val share = stringResource(Res.string.doctor_share)
    LumeList(
        items = listOf(Unit),
        key = { it },
        scrolls = false,
        background = Color.Transparent,
        chrome = LumeListChrome.Open,
    ) {
        LumeListRow(
            title = share,
            description = stringResource(Res.string.doctor_share_body),
            leading = LumeListAccessory.Tile(
                LumeIcon.Phone,
                LumeTileTint.Azure,
                emphasis = LumeListTileEmphasis.Washed,
            ),
            trailing = LumeListAccessory.Toggle(
                checked = profile.sharesContact,
                onCheckedChange = onShare,
                contentDescription = share,
            ),
            textStyle = LumeListRowTextStyle.Strong,
        )
    }
}

@Composable
private fun ProfileEntries(profile: DoctorProfile) {
    val score = profile.rating?.let { "${(it * TENTHS).toInt() / TENTHS_INT},${(it * TENTHS).toInt() % TENTHS_INT}" }
    LumeList(
        items = ProfileEntry.entries,
        key = { it },
        scrolls = false,
        background = Color.Transparent,
        chrome = LumeListChrome.OpenRuled,
    ) { entry ->
        when (entry) {
            ProfileEntry.RATINGS -> LumeListRow(
                title = stringResource(Res.string.doctor_ratings),
                description = score?.let {
                    pluralStringResource(Res.plurals.doctor_ratings_body, profile.ratingCount, it, profile.ratingCount)
                },
                leading = LumeListAccessory.Tile(LumeIcon.Star, LumeTileTint.Gold),
                disclosure = true,
                textStyle = LumeListRowTextStyle.Strong,
            )
            ProfileEntry.CORRECTIONS -> LumeListRow(
                title = stringResource(Res.string.doctor_corrections),
                description = pluralStringResource(
                    Res.plurals.doctor_corrections_body,
                    profile.pendingCorrections,
                    profile.pendingCorrections,
                ),
                leading = LumeListAccessory.Tile(LumeIcon.Flag, LumeTileTint.Amber),
                trailing = if (profile.pendingCorrections > 0) {
                    LumeListAccessory.Tag(profile.pendingCorrections.toString(), LumeTagStyle.Warning)
                } else {
                    LumeListAccessory.None
                },
                disclosure = true,
                textStyle = LumeListRowTextStyle.Strong,
            )
        }
    }
}

/** Not in m3: a session has to end somewhere, and Ajustes is where the patient's ends too (p7). */
@Composable
private fun SignOut(onSignOut: () -> Unit) {
    LumeList(
        items = listOf(Unit),
        key = { it },
        scrolls = false,
        background = Color.Transparent,
        chrome = LumeListChrome.Open,
        onRowTap = { onSignOut() },
    ) {
        LumeListRow(title = stringResource(Res.string.settings_sign_out), role = LumeListRowRole.Destructive)
    }
}

private const val TENTHS = 10.0
private const val TENTHS_INT = 10
