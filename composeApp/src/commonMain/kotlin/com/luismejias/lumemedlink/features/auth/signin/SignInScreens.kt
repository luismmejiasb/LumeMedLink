package com.luismejias.lumemedlink.features.auth.signin

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import cl.lume.uicomposer.components.LumeCard
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerAlignment
import cl.lume.uicomposer.components.LumeFieldGroup
import cl.lume.uicomposer.components.LumeFieldValidation
import cl.lume.uicomposer.components.LumeForm
import cl.lume.uicomposer.components.LumeIconTile
import cl.lume.uicomposer.components.LumeIconTileSize
import cl.lume.uicomposer.components.LumeLink
import cl.lume.uicomposer.components.LumeRevealLabels
import cl.lume.uicomposer.components.LumeRevealLook
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeStackAxis
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.core.input.SensitiveFieldFormat
import com.luismejias.lumemedlink.core.input.SensitiveFieldPurpose
import com.luismejias.lumemedlink.core.input.SensitiveTextField
import com.luismejias.lumemedlink.core.session.Clock
import com.luismejias.lumemedlink.features.auth.flow.AccessBack
import com.luismejias.lumemedlink.features.auth.flow.AccessBrand
import com.luismejias.lumemedlink.features.auth.flow.AccessHeader
import com.luismejias.lumemedlink.features.auth.flow.AccessPrimaryButton
import com.luismejias.lumemedlink.features.auth.flow.AccessTone
import com.luismejias.lumemedlink.features.auth.flow.ErrorBanner
import com.luismejias.lumemedlink.features.auth.flow.OfflineBanner
import com.luismejias.lumemedlink.features.auth.flow.ResultActions
import com.luismejias.lumemedlink.features.auth.flow.clockText
import com.luismejias.lumemedlink.features.auth.flow.secondsUntil
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.common_retry
import com.luismejias.lumemedlink.resources.common_understood
import com.luismejias.lumemedlink.resources.field_password
import com.luismejias.lumemedlink.resources.field_password_hide
import com.luismejias.lumemedlink.resources.field_password_hide_spoken
import com.luismejias.lumemedlink.resources.field_password_show
import com.luismejias.lumemedlink.resources.field_password_show_spoken
import com.luismejias.lumemedlink.resources.field_rut
import com.luismejias.lumemedlink.resources.field_rut_invalid
import com.luismejias.lumemedlink.resources.locked_body
import com.luismejias.lumemedlink.resources.locked_recover
import com.luismejias.lumemedlink.resources.locked_title
import com.luismejias.lumemedlink.resources.quick_activate
import com.luismejias.lumemedlink.resources.quick_activating
import com.luismejias.lumemedlink.resources.quick_body
import com.luismejias.lumemedlink.resources.quick_privacy
import com.luismejias.lumemedlink.resources.quick_title
import com.luismejias.lumemedlink.resources.quick_unavailable_body
import com.luismejias.lumemedlink.resources.quick_unavailable_restart
import com.luismejias.lumemedlink.resources.quick_unavailable_settings
import com.luismejias.lumemedlink.resources.quick_unavailable_title
import com.luismejias.lumemedlink.resources.sign_in_action
import com.luismejias.lumemedlink.resources.sign_in_check_password
import com.luismejias.lumemedlink.resources.sign_in_create
import com.luismejias.lumemedlink.resources.sign_in_forgot
import com.luismejias.lumemedlink.resources.sign_in_no_account
import com.luismejias.lumemedlink.resources.sign_in_running
import com.luismejias.lumemedlink.resources.sign_in_wrong_attempts
import com.luismejias.lumemedlink.resources.sign_in_wrong_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** 1b, with 1b-e1 (the callout and the red password) and 1b-e3 (the banner, "Reintentar") as its states. */
@Composable
internal fun SignInScreen(
    model: SignInModel,
    retryEpoch: Int,
    onBack: (() -> Unit)?,
    onForgot: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    val state by model.state.collectAsState()
    val wrong = state.wrongAttemptsLeft
    LumeForm(
        title = null,
        action = {
            AccessPrimaryButton(
                title = stringResource(if (state.offline) Res.string.common_retry else Res.string.sign_in_action),
                loadingMessage = stringResource(Res.string.sign_in_running),
                enabled = state.canSubmit,
                retryEpoch = retryEpoch,
                run = model::submit,
            )
        },
        // «¿No tienes cuenta? Créala» rests under the pinned action, as 1b draws it (ADR-0054).
        belowAction = {
            LumeStack(modifier = Modifier.fillMaxWidth(), alignment = LumeStackAlignment.Center) {
                LumeStack(
                    axis = LumeStackAxis.Horizontal,
                    alignment = LumeStackAlignment.Center,
                    spacing = { xSmall },
                ) {
                    LumeText(text = stringResource(Res.string.sign_in_no_account), color = LumeTextColor.Secondary)
                    LumeLink(title = stringResource(Res.string.sign_in_create), onClick = onCreateAccount)
                }
            }
        },
    ) {
        LumeStack(alignment = LumeStackAlignment.Start, spacing = { large }) {
            AccessBack(onBack)
            OfflineBanner(state.offline)
            AccessBrand()
            if (wrong != null) {
                ErrorBanner(
                    title = stringResource(Res.string.sign_in_wrong_title),
                    body = pluralStringResource(Res.plurals.sign_in_wrong_attempts, wrong, wrong),
                )
            }
            LumeFieldGroup {
                SensitiveTextField(
                    value = state.rut,
                    onValueChange = model::rutChanged,
                    purpose = SensitiveFieldPurpose.PERSONAL_DATA,
                    format = SensitiveFieldFormat.RUT,
                    label = stringResource(Res.string.field_rut),
                    // No placeholder on sign-in: the labels are enough (the author, 2026-10-13).
                    placeholder = "",
                    validation = if (state.rutInvalid) {
                        LumeFieldValidation.Error(stringResource(Res.string.field_rut_invalid))
                    } else {
                        LumeFieldValidation.Neutral
                    },
                )
                SensitiveTextField(
                    value = state.password,
                    onValueChange = model::passwordChanged,
                    purpose = SensitiveFieldPurpose.CREDENTIAL,
                    label = stringResource(Res.string.field_password),
                    placeholder = "",
                    reveal = LumeRevealLabels(
                        show = stringResource(Res.string.field_password_show_spoken),
                        hide = stringResource(Res.string.field_password_hide_spoken),
                    ),
                    revealLook = LumeRevealLook.Words(
                        stringResource(Res.string.field_password_show),
                        stringResource(Res.string.field_password_hide),
                    ),
                    validation = if (wrong != null) {
                        LumeFieldValidation.Error(stringResource(Res.string.sign_in_check_password))
                    } else {
                        LumeFieldValidation.Neutral
                    },
                )
            }
            if (!state.offline) {
                LumeStack(modifier = Modifier.fillMaxWidth(), alignment = LumeStackAlignment.End) {
                    LumeLink(title = stringResource(Res.string.sign_in_forgot), onClick = onForgot)
                }
            }
        }
    }
}

/** 1b-e2. The countdown is the server's instant, read against the clock. */
@Composable
internal fun LockedOutScreen(model: SignInModel, clock: Clock, onRecover: () -> Unit, onUnderstood: () -> Unit) {
    val state by model.state.collectAsState()
    val seconds = secondsUntil(state.lockedUntilEpochMillis, clock) ?: 0L
    LumeContainer(
        alignment = LumeContainerAlignment.CenterAbovePinnedAction,
        action = {
            ResultActions(
                primary = stringResource(Res.string.locked_recover),
                onPrimary = onRecover,
                secondary = stringResource(Res.string.common_understood),
                onSecondary = onUnderstood,
            )
        },
    ) {
        AccessHeader(
            icon = LumeIcon.Clock,
            title = stringResource(Res.string.locked_title),
            body = stringResource(Res.string.locked_body, clockText(seconds)),
            tone = AccessTone.WAIT,
        )
    }
}

/** 1c. Turning it on is required (ADR-0037): there is no "Ahora no". */
@Composable
internal fun QuickSignInScreen(model: QuickSignInModel, retryEpoch: Int) {
    val state by model.state.collectAsState()
    LumeContainer(
        alignment = LumeContainerAlignment.CenterAbovePinnedAction,
        action = {
            AccessPrimaryButton(
                title = stringResource(if (state.offline) Res.string.common_retry else Res.string.quick_activate),
                loadingMessage = stringResource(Res.string.quick_activating),
                enabled = true,
                retryEpoch = retryEpoch,
                run = model::activate,
            )
        },
    ) {
        LumeStack(spacing = { large }) {
            OfflineBanner(state.offline)
            AccessHeader(
                icon = LumeIcon.FaceId,
                title = stringResource(Res.string.quick_title),
                body = stringResource(Res.string.quick_body),
            )
            LumeCard {
                LumeStack(axis = LumeStackAxis.Horizontal, alignment = LumeStackAlignment.Start) {
                    LumeIconTile(icon = LumeIcon.Lock, size = LumeIconTileSize.Row)
                    LumeText(text = stringResource(Res.string.quick_privacy), color = LumeTextColor.Secondary)
                }
            }
        }
    }
}

/** 1c-e1. Without "Seguir con contraseña" (ADR-0044, deviation 1): the settings, or the start. */
@Composable
internal fun QuickSignInUnavailableScreen(onOpenSettings: () -> Unit, onRestart: () -> Unit) {
    LumeContainer(
        alignment = LumeContainerAlignment.CenterAbovePinnedAction,
        action = {
            ResultActions(
                primary = stringResource(Res.string.quick_unavailable_settings),
                onPrimary = onOpenSettings,
                secondary = stringResource(Res.string.quick_unavailable_restart),
                onSecondary = onRestart,
            )
        },
    ) {
        AccessHeader(
            icon = LumeIcon.FaceId,
            title = stringResource(Res.string.quick_unavailable_title),
            body = stringResource(Res.string.quick_unavailable_body),
            tone = AccessTone.WAIT,
        )
    }
}
