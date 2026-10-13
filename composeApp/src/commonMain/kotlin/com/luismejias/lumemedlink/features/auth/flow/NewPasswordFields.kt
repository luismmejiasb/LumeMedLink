package com.luismejias.lumemedlink.features.auth.flow

import androidx.compose.runtime.Composable
import cl.lume.uicomposer.components.LumeFieldValidation
import cl.lume.uicomposer.components.LumeRequirement
import cl.lume.uicomposer.components.LumeRequirementList
import cl.lume.uicomposer.components.LumeRequirementState
import cl.lume.uicomposer.components.LumeRevealLabels
import cl.lume.uicomposer.components.LumeRevealLook
import com.luismejias.lumemedlink.core.input.SensitiveFieldPurpose
import com.luismejias.lumemedlink.core.input.SensitiveTextField
import com.luismejias.lumemedlink.features.auth.shared.NewPasswordEntry
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.common_requirement_met
import com.luismejias.lumemedlink.resources.common_requirement_unmet
import com.luismejias.lumemedlink.resources.field_password_hide
import com.luismejias.lumemedlink.resources.field_password_hide_spoken
import com.luismejias.lumemedlink.resources.field_password_placeholder
import com.luismejias.lumemedlink.resources.field_password_repeat
import com.luismejias.lumemedlink.resources.field_password_show
import com.luismejias.lumemedlink.resources.field_password_show_spoken
import com.luismejias.lumemedlink.resources.field_passwords_differ
import com.luismejias.lumemedlink.resources.password_rule_case
import com.luismejias.lumemedlink.resources.password_rule_digit
import com.luismejias.lumemedlink.resources.password_rule_length
import com.luismejias.lumemedlink.resources.password_rule_symbol
import org.jetbrains.compose.resources.stringResource

/** The two password fields and their live rules — 2b and 3c draw the same block. */
@Composable
internal fun NewPasswordFields(
    entry: NewPasswordEntry,
    onPassword: (String) -> Unit,
    onRepeated: (String) -> Unit,
    passwordLabel: String,
    extraError: String? = null,
    /** The dot mask in the empty fields: the sign-up's (2b) only; recovery shows none (the author, 2026-10-13). */
    placeholders: Boolean = true,
    /** «*» by both titles: the sign-up marks its required fields (the author, 2026-10-13). */
    required: Boolean = false,
) {
    val placeholder = if (placeholders) stringResource(Res.string.field_password_placeholder) else ""
    val reveal = LumeRevealLabels(
        show = stringResource(Res.string.field_password_show_spoken),
        hide = stringResource(Res.string.field_password_hide_spoken),
    )
    val revealLook = LumeRevealLook.Words(
        stringResource(Res.string.field_password_show),
        stringResource(Res.string.field_password_hide),
    )
    val rules = entry.rules
    fun stateOf(met: Boolean) = if (met) LumeRequirementState.Met else LumeRequirementState.Unmet
    // The two fields straight into the caller's form, whose group sets their rhythm; the checklist rides the first
    // field's `below`, against it rather than a reserved line away (ADR-0054).
    SensitiveTextField(
        value = entry.password,
        onValueChange = onPassword,
        purpose = SensitiveFieldPurpose.CREDENTIAL,
        label = passwordLabel,
        placeholder = placeholder,
        required = required,
        reveal = reveal,
        revealLook = revealLook,
        newCredential = true,
        validation = if (extraError != null) LumeFieldValidation.Error(extraError) else LumeFieldValidation.Neutral,
        below = {
            LumeRequirementList(
                requirements = listOf(
                    LumeRequirement(stateOf(rules.longEnough), stringResource(Res.string.password_rule_length)),
                    LumeRequirement(stateOf(rules.mixedCase), stringResource(Res.string.password_rule_case)),
                    LumeRequirement(stateOf(rules.hasDigit), stringResource(Res.string.password_rule_digit)),
                    LumeRequirement(stateOf(rules.hasSymbol), stringResource(Res.string.password_rule_symbol)),
                ),
                metStateDescription = stringResource(Res.string.common_requirement_met),
                unmetStateDescription = stringResource(Res.string.common_requirement_unmet),
            )
        },
    )
    SensitiveTextField(
        value = entry.repeated,
        onValueChange = onRepeated,
        purpose = SensitiveFieldPurpose.CREDENTIAL,
        label = stringResource(Res.string.field_password_repeat),
        placeholder = placeholder,
        required = required,
        reveal = reveal,
        revealLook = revealLook,
        newCredential = true,
        validation = when {
            entry.mismatch -> LumeFieldValidation.Error(stringResource(Res.string.field_passwords_differ))
            entry.matches -> LumeFieldValidation.Valid
            else -> LumeFieldValidation.Neutral
        },
    )
}
