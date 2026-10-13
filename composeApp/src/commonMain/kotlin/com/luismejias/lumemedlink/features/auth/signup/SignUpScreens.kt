package com.luismejias.lumemedlink.features.auth.signup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import cl.lume.uicomposer.components.LumeAvatar
import cl.lume.uicomposer.components.LumeButton
import cl.lume.uicomposer.components.LumeButtonContent
import cl.lume.uicomposer.components.LumeButtonEmphasis
import cl.lume.uicomposer.components.LumeButtonWidth
import cl.lume.uicomposer.components.LumeCallout
import cl.lume.uicomposer.components.LumeCalloutStyle
import cl.lume.uicomposer.components.LumeCard
import cl.lume.uicomposer.components.LumeConsentRow
import cl.lume.uicomposer.components.LumeContainer
import cl.lume.uicomposer.components.LumeContainerAlignment
import cl.lume.uicomposer.components.LumeFieldValidation
import cl.lume.uicomposer.components.LumeForm
import cl.lume.uicomposer.components.LumeLink
import cl.lume.uicomposer.components.LumeRegion
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
import cl.lume.uicomposer.components.LumeStackAxis
import cl.lume.uicomposer.components.LumeStepCaption
import cl.lume.uicomposer.components.LumeStepIndicator
import cl.lume.uicomposer.components.LumeTag
import cl.lume.uicomposer.components.LumeTagStyle
import cl.lume.uicomposer.components.LumeText
import cl.lume.uicomposer.components.LumeTextColor
import cl.lume.uicomposer.components.LumeTextStyle
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.core.auth.AccountSummary
import com.luismejias.lumemedlink.core.input.OneTimeCodeField
import com.luismejias.lumemedlink.core.input.SensitiveFieldFormat
import com.luismejias.lumemedlink.core.input.SensitiveFieldPurpose
import com.luismejias.lumemedlink.core.input.SensitivePhoneField
import com.luismejias.lumemedlink.core.input.SensitiveTextField
import com.luismejias.lumemedlink.core.screen.richRuns
import com.luismejias.lumemedlink.core.session.Clock
import com.luismejias.lumemedlink.features.auth.flow.AccessBack
import com.luismejias.lumemedlink.features.auth.flow.AccessHeader
import com.luismejias.lumemedlink.features.auth.flow.AccessPrimaryButton
import com.luismejias.lumemedlink.features.auth.flow.AccessTitle
import com.luismejias.lumemedlink.features.auth.flow.AccessTone
import com.luismejias.lumemedlink.features.auth.flow.NewPasswordFields
import com.luismejias.lumemedlink.features.auth.flow.OfflineBanner
import com.luismejias.lumemedlink.features.auth.flow.ResultActions
import com.luismejias.lumemedlink.features.auth.flow.clockText
import com.luismejias.lumemedlink.features.auth.flow.initialsOf
import com.luismejias.lumemedlink.features.auth.flow.secondsUntil
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.code_expired_body
import com.luismejias.lumemedlink.resources.code_expired_change_phone
import com.luismejias.lumemedlink.resources.code_expired_resend
import com.luismejias.lumemedlink.resources.code_expired_title
import com.luismejias.lumemedlink.resources.code_wrong_attempts
import com.luismejias.lumemedlink.resources.common_continue
import com.luismejias.lumemedlink.resources.common_loading
import com.luismejias.lumemedlink.resources.common_retry
import com.luismejias.lumemedlink.resources.field_code
import com.luismejias.lumemedlink.resources.field_code_clear
import com.luismejias.lumemedlink.resources.field_email
import com.luismejias.lumemedlink.resources.field_email_invalid
import com.luismejias.lumemedlink.resources.field_first_name
import com.luismejias.lumemedlink.resources.field_first_surname
import com.luismejias.lumemedlink.resources.field_mobile
import com.luismejias.lumemedlink.resources.field_mobile_invalid
import com.luismejias.lumemedlink.resources.field_name_required
import com.luismejias.lumemedlink.resources.field_password
import com.luismejias.lumemedlink.resources.field_phone_invalid
import com.luismejias.lumemedlink.resources.field_rut
import com.luismejias.lumemedlink.resources.field_rut_invalid
import com.luismejias.lumemedlink.resources.field_second_name
import com.luismejias.lumemedlink.resources.field_second_surname
import com.luismejias.lumemedlink.resources.locked_recover
import com.luismejias.lumemedlink.resources.sign_in_action
import com.luismejias.lumemedlink.resources.sign_up_code_body
import com.luismejias.lumemedlink.resources.sign_up_code_resend
import com.luismejias.lumemedlink.resources.sign_up_code_resend_in
import com.luismejias.lumemedlink.resources.sign_up_code_title
import com.luismejias.lumemedlink.resources.sign_up_consents_needed
import com.luismejias.lumemedlink.resources.sign_up_create
import com.luismejias.lumemedlink.resources.sign_up_creating
import com.luismejias.lumemedlink.resources.sign_up_data_body
import com.luismejias.lumemedlink.resources.sign_up_data_title
import com.luismejias.lumemedlink.resources.sign_up_data_use_statement
import com.luismejias.lumemedlink.resources.sign_up_done_body
import com.luismejias.lumemedlink.resources.sign_up_done_title
import com.luismejias.lumemedlink.resources.sign_up_done_verified
import com.luismejias.lumemedlink.resources.sign_up_password_body
import com.luismejias.lumemedlink.resources.sign_up_password_title
import com.luismejias.lumemedlink.resources.sign_up_rut_taken
import com.luismejias.lumemedlink.resources.sign_up_rut_taken_body
import com.luismejias.lumemedlink.resources.sign_up_rut_taken_title
import com.luismejias.lumemedlink.resources.sign_up_step_data
import com.luismejias.lumemedlink.resources.sign_up_step_next
import com.luismejias.lumemedlink.resources.sign_up_step_of
import com.luismejias.lumemedlink.resources.sign_up_step_password
import com.luismejias.lumemedlink.resources.sign_up_step_phone
import com.luismejias.lumemedlink.resources.sign_up_telemetry_statement
import com.luismejias.lumemedlink.resources.sign_up_terms_statement
import com.luismejias.lumemedlink.shared.PhoneNumber
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val STEPS =
    listOf(Res.string.sign_up_step_data, Res.string.sign_up_step_password, Res.string.sign_up_step_phone)

/**
 * "Paso 1 de 3 · Tus datos" on the left, "Sigue: Contraseña" on the right, over the design's segmented bars — the kit's
 * step indicator as 2a–2c draw it (ADR-0044, deviation 5, closed).
 */
@Composable
private fun SignUpSteps(current: Int) {
    val caption = stringResource(Res.string.sign_up_step_of, current + 1, STEPS.size, stringResource(STEPS[current]))
    val next = STEPS.getOrNull(current + 1)?.let { stringResource(Res.string.sign_up_step_next, stringResource(it)) }
    // The design's segmented bars, the step on the left and what follows on the right (ADR-0044 deviation 5, closed).
    LumeStepIndicator(
        current = current,
        count = STEPS.size,
        contentDescription = listOfNotNull(caption, next).joinToString(". "),
        caption = LumeStepCaption(caption, next = next),
    )
}

@Composable
private fun errorOr(show: Boolean, message: StringResource): LumeFieldValidation =
    if (show) LumeFieldValidation.Error(stringResource(message)) else LumeFieldValidation.Neutral

/** 2a, with 2a-e1 (the fields in red) and 2a-e2 (the RUT exists: sign in or recover). */
@Composable
internal fun SignUpProfileScreen(
    model: SignUpProfileModel,
    retryEpoch: Int,
    onBack: (() -> Unit)?,
    onSignInInstead: (String) -> Unit,
    onRecoverInstead: () -> Unit,
) {
    val state by model.state.collectAsState()
    LumeForm(
        title = null,
        action = {
            AccessPrimaryButton(
                title = stringResource(if (state.offline) Res.string.common_retry else Res.string.common_continue),
                loadingMessage = stringResource(Res.string.common_loading),
                enabled = state.canSubmit,
                retryEpoch = retryEpoch,
                run = model::submit,
            )
        },
    ) {
        AccessBack(onBack)
        SignUpSteps(current = 0)
        OfflineBanner(state.offline)
        AccessTitle(stringResource(Res.string.sign_up_data_title), stringResource(Res.string.sign_up_data_body))
        SensitiveTextField(
            value = state.rut,
            onValueChange = model::rutChanged,
            purpose = SensitiveFieldPurpose.PERSONAL_DATA,
            format = SensitiveFieldFormat.RUT,
            label = stringResource(Res.string.field_rut),
            required = true,
            placeholder = "",
            validation = when {
                state.rutTaken -> LumeFieldValidation.Error(stringResource(Res.string.sign_up_rut_taken))
                state.rutValid -> LumeFieldValidation.Valid
                else -> errorOr(state.rutInvalid, Res.string.field_rut_invalid)
            },
        )
        if (state.rutTaken) {
            LumeCard {
                LumeStack(alignment = LumeStackAlignment.Start, spacing = { small }) {
                    LumeText(
                        text = stringResource(Res.string.sign_up_rut_taken_title),
                        style = LumeTextStyle.HeadingSmall,
                    )
                    LumeText(
                        text = stringResource(Res.string.sign_up_rut_taken_body),
                        color = LumeTextColor.Secondary,
                    )
                    LumeButton(
                        content = LumeButtonContent.Title(stringResource(Res.string.sign_in_action)),
                        width = LumeButtonWidth.Fill,
                        onClick = { onSignInInstead(state.rut) },
                    )
                    LumeButton(
                        content = LumeButtonContent.Title(stringResource(Res.string.locked_recover)),
                        emphasis = LumeButtonEmphasis.Outline,
                        width = LumeButtonWidth.Fill,
                        onClick = onRecoverInstead,
                    )
                }
            }
        } else {
            // The names in four; the required ones carry «*», and no field has a placeholder: the errors say
            // what is wrong (the author, 2026-10-13). One under the other at the form's full width: LumeEqualRow
            // sized each item by its text, so a long name widened the column past the screen (2026-10-13).
            SensitiveTextField(
                value = state.firstName,
                onValueChange = model::firstNameChanged,
                purpose = SensitiveFieldPurpose.PERSONAL_DATA,
                maxLength = NAME_MAX_LENGTH,
                format = SensitiveFieldFormat.NAME,
                label = stringResource(Res.string.field_first_name),
                required = true,
                placeholder = "",
                validation = errorOr(state.firstNameMissing, Res.string.field_name_required),
            )
            SensitiveTextField(
                value = state.secondName,
                onValueChange = model::secondNameChanged,
                purpose = SensitiveFieldPurpose.PERSONAL_DATA,
                maxLength = NAME_MAX_LENGTH,
                format = SensitiveFieldFormat.NAME,
                label = stringResource(Res.string.field_second_name),
                placeholder = "",
            )
            SensitiveTextField(
                value = state.firstSurname,
                onValueChange = model::firstSurnameChanged,
                purpose = SensitiveFieldPurpose.PERSONAL_DATA,
                maxLength = NAME_MAX_LENGTH,
                format = SensitiveFieldFormat.NAME,
                label = stringResource(Res.string.field_first_surname),
                required = true,
                placeholder = "",
                validation = errorOr(state.firstSurnameMissing, Res.string.field_name_required),
            )
            SensitiveTextField(
                value = state.secondSurname,
                onValueChange = model::secondSurnameChanged,
                purpose = SensitiveFieldPurpose.PERSONAL_DATA,
                maxLength = NAME_MAX_LENGTH,
                format = SensitiveFieldFormat.NAME,
                label = stringResource(Res.string.field_second_surname),
                placeholder = "",
            )
            val chilean = state.mobileDialCode == PhoneNumber.CHILE
            SensitivePhoneField(
                value = state.mobile,
                onValueChange = model::mobileChanged,
                region = LumeRegion.of(state.mobileRegion) ?: LumeRegion.of(CHILE_REGION)!!,
                onRegionChange = { model.mobileRegionChanged(it.code, it.dialCode) },
                label = stringResource(Res.string.field_mobile),
                required = true,
                placeholder = "",
                groups = if (chilean) PhoneNumber.CHILEAN_MOBILE_GROUPS else null,
                validation = errorOr(
                    state.mobileInvalid,
                    if (chilean) Res.string.field_mobile_invalid else Res.string.field_phone_invalid,
                ),
            )
            SensitiveTextField(
                value = state.email,
                onValueChange = model::emailChanged,
                purpose = SensitiveFieldPurpose.PERSONAL_DATA,
                format = SensitiveFieldFormat.EMAIL,
                label = stringResource(Res.string.field_email),
                required = true,
                placeholder = "",
                validation = errorOr(state.emailInvalid, Res.string.field_email_invalid),
            )
        }
    }
}

/** 2b, with 2b-e1 (no match; a consent still missing). */
@Composable
internal fun SignUpCredentialsScreen(model: SignUpCredentialsModel, retryEpoch: Int, onBack: (() -> Unit)?) {
    val state by model.state.collectAsState()
    LumeForm(
        title = null,
        action = {
            AccessPrimaryButton(
                title = stringResource(if (state.offline) Res.string.common_retry else Res.string.common_continue),
                loadingMessage = stringResource(Res.string.common_loading),
                enabled = state.canSubmit,
                retryEpoch = retryEpoch,
                run = model::submit,
            )
        },
        footer = {
            // The consents are part of "Continuar": pinned over it, under the keyboard (the author, ADR-0054).
            LumeStack(alignment = LumeStackAlignment.Start, spacing = { small }) {
                // One message for the missing consents, no row marked (the author, 2026-10-13). The optional one
                // keeps saying it is optional: hiding that would make its consent a deceived one (Ley 21.719,
                // ADR-0052), so it was not built.
                if (state.consentRequired) {
                    LumeCallout(
                        text = stringResource(Res.string.sign_up_consents_needed),
                        style = LumeCalloutStyle.Error,
                        icon = LumeIcon.Alert,
                    )
                }
                // One line each, the link inside, as 2b draws it. The links open nothing yet: there is no terms
                // document to open (ADR-0044, deviation 12).
                LumeConsentRow(
                    granted = state.acceptedTerms,
                    onGrantedChange = model::termsChanged,
                    text = stringResource(Res.string.sign_up_terms_statement).richRuns(onLink = {}),
                )
                // Second, between the required two (the author, 2026-10-13), and still optional and saying so (ADR-0051
                // §4): a consent the person cannot refuse is not consent. Google is named in the privacy notice, not
                // here (ADR-0052 §9).
                LumeConsentRow(
                    granted = state.acceptedTelemetry,
                    onGrantedChange = model::telemetryChanged,
                    text = stringResource(Res.string.sign_up_telemetry_statement).richRuns(),
                )
                LumeConsentRow(
                    granted = state.acceptedDataUse,
                    onGrantedChange = model::dataUseChanged,
                    text = stringResource(Res.string.sign_up_data_use_statement).richRuns(onLink = {}),
                )
            }
        },
    ) {
        AccessBack(onBack)
        SignUpSteps(current = 1)
        OfflineBanner(state.offline)
        AccessTitle(
            stringResource(Res.string.sign_up_password_title),
            stringResource(Res.string.sign_up_password_body),
        )
        NewPasswordFields(
            entry = state.entry,
            onPassword = model::passwordChanged,
            onRepeated = model::repeatedChanged,
            passwordLabel = stringResource(Res.string.field_password),
            placeholders = false,
            required = true,
        )
    }
}

/** 2c, with 2c-e1 inline. Resend opens when the server's interval passes. */
@Composable
internal fun SignUpCodeScreen(model: SignUpCodeModel, clock: Clock, retryEpoch: Int, onBack: (() -> Unit)?) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    val wrong = state.entry.wrongAttemptsLeft
    val resendIn = secondsUntil(state.dispatch?.resendAvailableAtEpochMillis, clock) ?: 0L
    LumeForm(
        title = null,
        action = {
            AccessPrimaryButton(
                title = stringResource(if (state.entry.offline) Res.string.common_retry else Res.string.sign_up_create),
                loadingMessage = stringResource(Res.string.sign_up_creating),
                enabled = state.entry.complete && wrong == null,
                retryEpoch = retryEpoch,
                run = model::submit,
            )
        },
    ) {
        LumeStack(spacing = { large }) {
            AccessBack(onBack)
            SignUpSteps(current = 2)
            OfflineBanner(state.entry.offline)
            AccessHeader(
                icon = LumeIcon.Mobile,
                title = stringResource(Res.string.sign_up_code_title),
                body = stringResource(Res.string.sign_up_code_body, state.maskedMobile),
            )
            OneTimeCodeField(
                code = state.entry.code,
                onCodeChange = model::codeChanged,
                label = stringResource(Res.string.field_code),
                clearContentDescription = stringResource(Res.string.field_code_clear),
                validation = if (wrong != null) {
                    LumeFieldValidation.Error(pluralStringResource(Res.plurals.code_wrong_attempts, wrong, wrong))
                } else {
                    LumeFieldValidation.Neutral
                },
            )
            if (resendIn > 0) {
                LumeText(
                    text = stringResource(Res.string.sign_up_code_resend_in, clockText(resendIn)),
                    color = LumeTextColor.Secondary,
                )
            } else {
                LumeLink(title = stringResource(Res.string.sign_up_code_resend), onClick = {
                    scope.launch { model.resend() }
                })
            }
        }
    }
}

/** 2c-e2: a new code, or back to the mobile. */
@Composable
internal fun SignUpCodeExpiredScreen(model: SignUpCodeModel, retryEpoch: Int, onBack: (() -> Unit)?) {
    LumeContainer(
        alignment = LumeContainerAlignment.CenterAbovePinnedAction,
        action = {
            LumeStack(spacing = { small }) {
                AccessPrimaryButton(
                    title = stringResource(Res.string.code_expired_resend),
                    loadingMessage = stringResource(Res.string.common_loading),
                    enabled = true,
                    retryEpoch = retryEpoch,
                    run = model::resend,
                )
                LumeButton(
                    content = LumeButtonContent.Title(stringResource(Res.string.code_expired_change_phone)),
                    emphasis = LumeButtonEmphasis.Plain,
                    width = LumeButtonWidth.Fill,
                    onClick = model::changePhone,
                )
            }
        },
    ) {
        LumeStack(spacing = { large }) {
            AccessBack(onBack)
            AccessHeader(
                icon = LumeIcon.Clock,
                title = stringResource(Res.string.code_expired_title),
                body = stringResource(Res.string.code_expired_body),
                tone = AccessTone.WAIT,
            )
        }
    }
}

/** 2d. Who the account belongs to, and on to quick sign-in. */
@Composable
internal fun SignUpDoneScreen(account: AccountSummary?, onContinue: () -> Unit) {
    LumeContainer(
        alignment = LumeContainerAlignment.CenterAbovePinnedAction,
        action = { ResultActions(primary = stringResource(Res.string.common_continue), onPrimary = onContinue) },
    ) {
        LumeStack(spacing = { large }) {
            AccessHeader(
                icon = LumeIcon.Check,
                title = stringResource(Res.string.sign_up_done_title, account?.givenNames.orEmpty()),
                body = stringResource(Res.string.sign_up_done_body),
                tone = AccessTone.SUCCESS,
            )
            if (account != null) {
                val name = "${account.givenNames} ${account.familyNames}"
                LumeCard {
                    LumeStack(axis = LumeStackAxis.Horizontal, alignment = LumeStackAlignment.Center) {
                        LumeAvatar(
                            initials = initialsOf(account.givenNames, account.familyNames),
                            contentDescription = name,
                        )
                        LumeStack(alignment = LumeStackAlignment.Start, spacing = { xSmall }) {
                            LumeText(text = name, style = LumeTextStyle.HeadingSmall)
                            LumeText(text = account.rut.display, color = LumeTextColor.Secondary)
                        }
                        LumeTag(text = stringResource(Res.string.sign_up_done_verified), style = LumeTagStyle.Success)
                    }
                }
            }
        }
    }
}
