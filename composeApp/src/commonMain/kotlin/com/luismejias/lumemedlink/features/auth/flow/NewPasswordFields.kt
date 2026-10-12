package com.luismejias.lumemedlink.features.auth.flow

import androidx.compose.runtime.Composable
import cl.lume.uicomposer.components.LumeFieldSubmit
import cl.lume.uicomposer.components.LumeFieldValidation
import cl.lume.uicomposer.components.LumeRequirement
import cl.lume.uicomposer.components.LumeRequirementList
import cl.lume.uicomposer.components.LumeRequirementState
import cl.lume.uicomposer.components.LumeRevealLabels
import cl.lume.uicomposer.components.LumeRevealLook
import cl.lume.uicomposer.components.LumeStack
import cl.lume.uicomposer.components.LumeStackAlignment
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
import org.jetbrains.compose.resources.stringResource

/** The two password fields and their live rules — 2b and 3c draw the same block. */
@Composable
internal fun NewPasswordFields(
    entry: NewPasswordEntry,
    onPassword: (String) -> Unit,
    onRepeated: (String) -> Unit,
    passwordLabel: String,
    extraError: String? = null,
) {
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
    LumeStack(alignment = LumeStackAlignment.Start, spacing = { medium }) {
        SensitiveTextField(
            value = entry.password,
            onValueChange = onPassword,
            purpose = SensitiveFieldPurpose.CREDENTIAL,
            label = passwordLabel,
            placeholder = stringResource(Res.string.field_password_placeholder),
            reveal = reveal,
            revealLook = revealLook,
            newCredential = true,
            validation = if (extraError != null) LumeFieldValidation.Error(extraError) else LumeFieldValidation.Neutral,
            submit = LumeFieldSubmit.Next,
        )
        LumeRequirementList(
            requirements = listOf(
                LumeRequirement(stateOf(rules.longEnough), stringResource(Res.string.password_rule_length)),
                LumeRequirement(stateOf(rules.mixedCase), stringResource(Res.string.password_rule_case)),
                LumeRequirement(stateOf(rules.hasDigit), stringResource(Res.string.password_rule_digit)),
            ),
            metStateDescription = stringResource(Res.string.common_requirement_met),
            unmetStateDescription = stringResource(Res.string.common_requirement_unmet),
        )
        SensitiveTextField(
            value = entry.repeated,
            onValueChange = onRepeated,
            purpose = SensitiveFieldPurpose.CREDENTIAL,
            label = stringResource(Res.string.field_password_repeat),
            placeholder = stringResource(Res.string.field_password_placeholder),
            reveal = reveal,
            revealLook = revealLook,
            newCredential = true,
            validation = when {
                entry.mismatch -> LumeFieldValidation.Error(stringResource(Res.string.field_passwords_differ))
                entry.matches -> LumeFieldValidation.Valid
                else -> LumeFieldValidation.Neutral
            },
            submit = LumeFieldSubmit.Done,
        )
    }
}
