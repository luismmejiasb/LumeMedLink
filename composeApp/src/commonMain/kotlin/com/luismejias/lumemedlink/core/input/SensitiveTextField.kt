package com.luismejias.lumemedlink.core.input

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

/**
 * What a field holds, because the correct keyboard hardening is NOT the same for both (F3,
 * ADR-0013).
 *
 * Blanket-disabling everything would be cargo cult and would actually hurt security: a password
 * field SHOULD accept a password manager, because manager-generated passwords beat memorized ones.
 * What must never happen is the keyboard *learning* the value and offering it later somewhere else.
 */
internal enum class SensitiveFieldPurpose {
    /** A person's data: RUT, phone, email, name. The keyboard must not learn or suggest it. */
    PERSONAL_DATA,

    /** A secret the user authenticates with. Masked, unlearnable, but fillable by a manager. */
    CREDENTIAL,
}

/**
 * The ONE way this app takes text that matters (ADR-0013). Every field carrying personal data or a
 * credential goes through here, and `Scripts/check-input-surfaces.sh` fails the build on a raw
 * `BasicTextField`/`TextField` outside this file.
 *
 * Why a choke point instead of a rule per screen: keyboard and autofill hardening is a list of
 * small attributes that each screen would have to remember, and the one screen that forgets is the
 * one that leaks a RUT into a third-party keyboard's dictionary. Here the attributes are decided
 * once, and the day the platform exposes more of them they land in a single file rather than in N.
 *
 * What it does TODAY, on both platforms: no autocorrect and no capitalization suggestions (so a
 * RUT or a surname never enters the keyboard's learned vocabulary through that path), and for a
 * credential, the password keyboard type plus masking — IMEs are required not to learn from a
 * password field.
 *
 * What it does NOT do yet, declared rather than implied (ADR-0013 lists these): Android's
 * `IME_FLAG_NO_PERSONALIZED_LEARNING` and explicit autofill exclusion are not reachable from
 * common Compose in the pinned version, and iOS's app-wide third-party keyboard veto needs the iOS
 * host that does not exist. Those land in this file when they become reachable.
 *
 * Deliberately unstyled: the design system (S0.3) is deferred, and a styled primitive here would
 * be the first hardcoded-style violation. S0.3 dresses it; the security attributes stay.
 */
@Composable
internal fun SensitiveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    purpose: SensitiveFieldPurpose,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        keyboardOptions = keyboardOptionsFor(purpose, keyboardType),
        visualTransformation = if (isMasked(purpose)) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
    )
}

/**
 * The keyboard hardening decisions, pulled out of the composable so a test can reach them — a
 * security property nobody can assert is a security property nobody is keeping.
 */
internal fun keyboardOptionsFor(
    purpose: SensitiveFieldPurpose,
    keyboardType: KeyboardType = KeyboardType.Text,
): KeyboardOptions = KeyboardOptions(
    // A credential is always typed on the password keyboard, whatever the caller asked for: IMEs
    // are required not to learn from a password field, and that guarantee is not the caller's to
    // trade away.
    keyboardType = if (purpose == SensitiveFieldPurpose.CREDENTIAL) KeyboardType.Password else keyboardType,
    // Both off for every purpose: autocorrect and auto-capitalization are the paths by which a
    // typed value reaches the keyboard's learned vocabulary and resurfaces as a suggestion in
    // another app — the leak is the suggestion, not the typing.
    autoCorrectEnabled = false,
    capitalization = KeyboardCapitalization.None,
)

/** Credentials are masked; personal data is not — a doctor must be able to check a phone number. */
internal fun isMasked(purpose: SensitiveFieldPurpose): Boolean = purpose == SensitiveFieldPurpose.CREDENTIAL
