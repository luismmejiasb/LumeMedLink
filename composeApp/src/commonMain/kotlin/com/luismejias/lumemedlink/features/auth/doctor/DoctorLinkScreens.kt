package com.luismejias.lumemedlink.features.auth.doctor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cl.lume.uicomposer.components.LumeAvatar
import cl.lume.uicomposer.components.LumeAvatarSize
import cl.lume.uicomposer.components.LumeButton
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeButtonEmphasis
import cl.lume.uicomposer.components.LumeButtonWidth
import cl.lume.uicomposer.components.LumeCard
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerAlignment
import cl.lume.uicomposer.components.LumeContainerScroll
import cl.lume.uicomposer.components.LumeFieldState
import cl.lume.uicomposer.components.LumeFieldSubmit
import cl.lume.uicomposer.components.LumeFieldValidation
import cl.lume.uicomposer.components.LumeListAccessory
import cl.lume.uicomposer.components.LumeListRow
import cl.lume.uicomposer.components.LumeListRowTextStyle
import cl.lume.uicomposer.components.LumeListTileEmphasis
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeTag
import cl.lume.uicomposer.components.LumeTagStyle
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextAlignment
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTextStyle
import cl.lume.uicomposer.foundations.LumeIcon
import cl.lume.uicomposer.tokens.LumeTileTint
import com.luismejias.lumemedlink.core.input.SensitiveFieldPurpose
import com.luismejias.lumemedlink.core.input.SensitiveTextField
import com.luismejias.lumemedlink.features.auth.flow.AccessBack
import com.luismejias.lumemedlink.features.auth.flow.AccessHeader
import com.luismejias.lumemedlink.features.auth.flow.AccessPrimaryButton
import com.luismejias.lumemedlink.features.auth.flow.AccessTitle
import com.luismejias.lumemedlink.features.auth.flow.AccessTone
import com.luismejias.lumemedlink.features.auth.flow.OfflineBanner
import com.luismejias.lumemedlink.features.auth.flow.ResultActions
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.common_retry
import com.luismejias.lumemedlink.resources.common_understood
import com.luismejias.lumemedlink.resources.doctor_confirm_body
import com.luismejias.lumemedlink.resources.doctor_confirm_linking
import com.luismejias.lumemedlink.resources.doctor_confirm_no
import com.luismejias.lumemedlink.resources.doctor_confirm_rut
import com.luismejias.lumemedlink.resources.doctor_confirm_scope
import com.luismejias.lumemedlink.resources.doctor_confirm_title
import com.luismejias.lumemedlink.resources.doctor_confirm_verified
import com.luismejias.lumemedlink.resources.doctor_confirm_yes
import com.luismejias.lumemedlink.resources.doctor_link_action
import com.luismejias.lumemedlink.resources.doctor_link_body
import com.luismejias.lumemedlink.resources.doctor_link_field
import com.luismejias.lumemedlink.resources.doctor_link_placeholder
import com.luismejias.lumemedlink.resources.doctor_link_running
import com.luismejias.lumemedlink.resources.doctor_link_title
import com.luismejias.lumemedlink.resources.doctor_linked_agenda
import com.luismejias.lumemedlink.resources.doctor_linked_agenda_body
import com.luismejias.lumemedlink.resources.doctor_linked_body
import com.luismejias.lumemedlink.resources.doctor_linked_patients
import com.luismejias.lumemedlink.resources.doctor_linked_patients_body
import com.luismejias.lumemedlink.resources.doctor_linked_profile
import com.luismejias.lumemedlink.resources.doctor_linked_profile_body
import com.luismejias.lumemedlink.resources.doctor_linked_start
import com.luismejias.lumemedlink.resources.doctor_linked_title
import com.luismejias.lumemedlink.resources.doctor_spent_body
import com.luismejias.lumemedlink.resources.doctor_spent_hint
import com.luismejias.lumemedlink.resources.doctor_spent_retry
import com.luismejias.lumemedlink.resources.doctor_spent_title
import com.luismejias.lumemedlink.resources.doctor_unverified_body
import com.luismejias.lumemedlink.resources.doctor_unverified_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** 4a — the typed code only; the QR viewfinder and its camera state wait for their ADR (ADR-0044, deviation 3). */
@Composable
internal fun DoctorCodeScreen(model: DoctorLinkModel, retryEpoch: Int, onBack: (() -> Unit)?) {
    val state by model.codeState.collectAsState()
    LumeContainer(
        scroll = LumeContainerScroll.Scrollable,
        action = {
            AccessPrimaryButton(
                title = stringResource(if (state.offline) Res.string.common_retry else Res.string.doctor_link_action),
                loadingMessage = stringResource(Res.string.doctor_link_running),
                enabled = state.canSubmit,
                retryEpoch = retryEpoch,
                run = model::lookUp,
            )
        },
    ) {
        LumeStack(spacing = { large }) {
            AccessBack(onBack)
            OfflineBanner(state.offline)
            AccessHeader(
                icon = LumeIcon.Stethoscope,
                title = stringResource(Res.string.doctor_link_title),
                body = stringResource(Res.string.doctor_link_body),
            )
            // A one-time code, not personal data — but every typed value goes through core/input (ADR-0013), and
            // the personal-data path keeps the keyboard from learning it.
            SensitiveTextField(
                value = state.typed,
                onValueChange = model::codeChanged,
                purpose = SensitiveFieldPurpose.PERSONAL_DATA,
                label = stringResource(Res.string.doctor_link_field),
                placeholder = stringResource(Res.string.doctor_link_placeholder),
                submit = LumeFieldSubmit.Go,
            )
        }
    }
}

/** 4a-e1. "Escanear de nuevo" becomes "Escribir otro código" while there is no scanner. */
@Composable
internal fun DoctorCodeSpentScreen(model: DoctorLinkModel, onBack: (() -> Unit)?) {
    val state by model.codeState.collectAsState()
    LumeContainer(
        scroll = LumeContainerScroll.Scrollable,
        action = {
            ResultActions(primary = stringResource(Res.string.doctor_spent_retry), onPrimary = model::typeAnother)
        },
    ) {
        LumeStack(spacing = { large }) {
            AccessBack(onBack)
            AccessHeader(
                icon = LumeIcon.Clock,
                title = stringResource(Res.string.doctor_spent_title),
                body = stringResource(Res.string.doctor_spent_body),
                tone = AccessTone.WAIT,
            )
            SensitiveTextField(
                value = state.typed,
                onValueChange = {},
                purpose = SensitiveFieldPurpose.PERSONAL_DATA,
                label = stringResource(Res.string.doctor_link_field),
                placeholder = stringResource(Res.string.doctor_link_placeholder),
                validation = LumeFieldValidation.Error(stringResource(Res.string.doctor_spent_hint)),
                state = LumeFieldState.Disabled,
            )
        }
    }
}

/** 4b: whose LumeMed account the code points to, and the promise of what this app shows. */
@Composable
internal fun DoctorConfirmScreen(model: DoctorLinkModel, retryEpoch: Int, onBack: (() -> Unit)?) {
    val state by model.confirmState.collectAsState()
    val doctor = state.doctor
    LumeContainer(
        scroll = LumeContainerScroll.Scrollable,
        action = {
            LumeStack(spacing = { small }) {
                AccessPrimaryButton(
                    title = stringResource(
                        if (state.offline) Res.string.common_retry else Res.string.doctor_confirm_yes,
                    ),
                    loadingMessage = stringResource(Res.string.doctor_confirm_linking),
                    enabled = doctor != null,
                    retryEpoch = retryEpoch,
                    run = model::confirmLink,
                )
                LumeButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.doctor_confirm_no)),
                    emphasis = LumeButtonEmphasis.Plain,
                    width = LumeButtonWidth.Fill,
                    onClick = model::notMe,
                )
            }
        },
    ) {
        LumeStack(alignment = LumeStackAlignment.Start, spacing = { large }) {
            AccessBack(onBack)
            OfflineBanner(state.offline)
            AccessTitle(stringResource(Res.string.doctor_confirm_title), stringResource(Res.string.doctor_confirm_body))
            if (doctor != null) {
                LumeCard(alignment = LumeStackAlignment.Center) {
                    LumeStack(spacing = { small }) {
                        LumeAvatar(
                            initials = initialsOfDisplayName(doctor.displayName),
                            size = LumeAvatarSize.Large,
                            tint = LumeTileTint.Teal,
                            contentDescription = doctor.displayName,
                        )
                        LumeText(
                            text = doctor.displayName,
                            style = LumeTextStyle.HeadingMedium,
                            alignment = LumeTextAlignment.Center,
                        )
                        LumeText(
                            text = "${doctor.specialty} · ${doctor.practice}",
                            color = LumeTextColor.Secondary,
                            alignment = LumeTextAlignment.Center,
                        )
                        LumeText(
                            text = stringResource(Res.string.doctor_confirm_rut, doctor.rut.display),
                            color = LumeTextColor.Secondary,
                        )
                        LumeTag(text = stringResource(Res.string.doctor_confirm_verified), style = LumeTagStyle.Success)
                    }
                }
            }
            LumeText(text = stringResource(Res.string.doctor_confirm_scope), color = LumeTextColor.Secondary)
        }
    }
}

/** 4b-e1. Nothing to do here but wait for LumeMed; back to the start. */
@Composable
internal fun DoctorUnverifiedScreen(onUnderstood: () -> Unit) {
    LumeContainer(
        alignment = LumeContainerAlignment.CenterAbovePinnedAction,
        action = { ResultActions(primary = stringResource(Res.string.common_understood), onPrimary = onUnderstood) },
    ) {
        AccessHeader(
            icon = LumeIcon.Clock,
            title = stringResource(Res.string.doctor_unverified_title),
            body = stringResource(Res.string.doctor_unverified_body),
            tone = AccessTone.WAIT,
        )
    }
}

/** 4c: what the doctor will find here — and nothing clinical. */
@Composable
internal fun DoctorLinkedScreen(model: DoctorLinkModel) {
    val state by model.confirmState.collectAsState()
    val name = state.doctor?.displayName.orEmpty()
    LumeContainer(
        alignment = LumeContainerAlignment.CenterAbovePinnedAction,
        action = { ResultActions(primary = stringResource(Res.string.doctor_linked_start), onPrimary = model::start) },
    ) {
        LumeStack(spacing = { large }) {
            AccessHeader(
                icon = LumeIcon.Check,
                title = stringResource(Res.string.doctor_linked_title),
                body = stringResource(Res.string.doctor_linked_body, name),
                tone = AccessTone.SUCCESS,
            )
            // Three cards, one per thing the doctor will find, as 4c draws them.
            LumeStack(spacing = { small }) {
                LinkedRow(Res.string.doctor_linked_profile, Res.string.doctor_linked_profile_body, LumeIcon.Stethoscope)
                LinkedRow(Res.string.doctor_linked_agenda, Res.string.doctor_linked_agenda_body, LumeIcon.Clock)
                LinkedRow(Res.string.doctor_linked_patients, Res.string.doctor_linked_patients_body, LumeIcon.Mobile)
            }
        }
    }
}

@Composable
private fun LinkedRow(title: StringResource, body: StringResource, icon: LumeIcon) {
    LumeCard(modifier = Modifier.fillMaxWidth()) {
        LumeListRow(
            title = stringResource(title),
            description = stringResource(body),
            leading = LumeListAccessory.Tile(icon, LumeTileTint.Azure, emphasis = LumeListTileEmphasis.Washed),
            textStyle = LumeListRowTextStyle.Strong,
        )
    }
}

/** "MR" for "Dra. Mariana Rendón": the title before the name does not count. */
private fun initialsOfDisplayName(displayName: String): String {
    val words = displayName.split(' ').filter { it.isNotBlank() && !it.endsWith('.') }
    return listOfNotNull(
        words.firstOrNull()?.first(),
        words.drop(1).firstOrNull()?.first(),
    ).joinToString("").uppercase()
}
