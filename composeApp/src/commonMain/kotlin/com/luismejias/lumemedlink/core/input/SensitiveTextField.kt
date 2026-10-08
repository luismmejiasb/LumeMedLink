package com.luismejias.lumemedlink.core.input

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cl.lume.uicomposer.components.LumeDocumentField
import cl.lume.uicomposer.components.LumeDocumentFormat
import cl.lume.uicomposer.components.LumeFieldKeyboard
import cl.lume.uicomposer.components.LumeFieldState
import cl.lume.uicomposer.components.LumeFieldSubmit
import cl.lume.uicomposer.components.LumeFieldValidation
import cl.lume.uicomposer.components.LumePasswordField
import cl.lume.uicomposer.components.LumeRevealLabels
import cl.lume.uicomposer.components.LumeTextCase
import cl.lume.uicomposer.components.LumeTextField

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

/** Which keyboard a personal datum needs. A credential ignores it: it is always the password keyboard. */
internal enum class SensitiveFieldFormat {
    /** Free text: a name, an address. */
    TEXT,
    EMAIL,
    PHONE,

    /** A Chilean RUT: the kit's document field, which formats and limits it as typed. */
    RUT,
}

/**
 * What the kit is asked for, pulled out of the composable so a test can reach it — a security
 * property nobody can assert is a security property nobody is keeping.
 */
internal data class KitFieldRequest(
    val masked: Boolean,
    val keyboard: LumeFieldKeyboard,
    val textCase: LumeTextCase,
    /** The kit's document field instead of its text field: it formats the RUT as typed. */
    val document: Boolean = false,
)

/**
 * The decision this file exists for. Every personal datum is typed [LumeTextCase.Verbatim] — neither
 * capitals nor corrections, the path by which a typed RUT or surname enters the keyboard's learned
 * vocabulary and resurfaces as a suggestion in another app (ADR-0013). The kit's other cases keep the
 * platform's corrections on free text, which is right for prose and exactly wrong here. That the kit
 * turns Verbatim into "no autocorrect, no capitalization" is the KIT's contract and the kit's test
 * (`TextCaseTests`); what this repo owns is that no personal datum is ever asked for any other way.
 */
internal fun kitFieldRequestFor(purpose: SensitiveFieldPurpose, format: SensitiveFieldFormat): KitFieldRequest =
    when (purpose) {
        // The format is ignored on purpose: "IMEs do not learn from a password field" is not the
        // caller's guarantee to trade away for an email keyboard.
        SensitiveFieldPurpose.CREDENTIAL -> KitFieldRequest(
            masked = true,
            keyboard = LumeFieldKeyboard.Default,
            textCase = LumeTextCase.Verbatim,
        )
        SensitiveFieldPurpose.PERSONAL_DATA -> KitFieldRequest(
            masked = false,
            keyboard = when (format) {
                SensitiveFieldFormat.TEXT -> LumeFieldKeyboard.Default
                SensitiveFieldFormat.EMAIL -> LumeFieldKeyboard.Email
                SensitiveFieldFormat.PHONE -> LumeFieldKeyboard.Phone
                // The document field decides its own keyboard; this value is not used for it.
                SensitiveFieldFormat.RUT -> LumeFieldKeyboard.Default
            },
            textCase = LumeTextCase.Verbatim,
            document = format == SensitiveFieldFormat.RUT,
        )
    }

/**
 * The ONE way this app takes text that matters (ADR-0013). Every field carrying personal data or a
 * credential goes through here, and `Scripts/check-input-surfaces.sh` fails the build on a raw
 * `BasicTextField`/`TextField`, or on any of the design kit's input fields, outside `core/input/`.
 *
 * Why a choke point instead of a rule per screen: keyboard and autofill hardening is a list of
 * small attributes that each screen would have to remember, and the one screen that forgets is the
 * one that leaks a RUT into a third-party keyboard's dictionary. Here the attributes are decided
 * once, and the day the platform exposes more of them they land in a single file rather than in N.
 *
 * Since S0.3 (task 0013, ADR-0033) the field itself is LumeUIComposer's: the look is the kit's, the
 * keyboard decision stays here ([kitFieldRequestFor]). A credential is the kit's password field, which
 * carries the password keyboard, masking and credential autofill; a personal datum is the kit's text
 * field, typed verbatim.
 *
 * Copying inside the field is the platform's and is allowed (ADR-0013 amended 2026-10-06): it is
 * what the person typed. Displayed data stays uncopyable — that rule lives in the gate, not here.
 *
 * What it does NOT do yet, declared rather than implied (ADR-0013 lists these): Android's
 * `IME_FLAG_NO_PERSONALIZED_LEARNING` is not reachable from common Compose in the pinned version and
 * lands here — or in the kit — when it is. iOS's app-wide third-party keyboard veto is NOT this file's:
 * it lives in the host (`AppDelegate.shouldAllowExtensionPointIdentifier`).
 *
 * Autofill is not decided here either. It is not reachable *per field*: every Compose text field
 * publishes `ContentDataType.Text` semantics unconditionally. It IS reachable per *window*, which is
 * why the control lives in `core/input/StructureExport.kt` and the Android shell (ADR-0024). The
 * exclusion is window-wide, so the day a credential screen ships it has to ask for autofill back
 * explicitly — a password manager is a security *gain* and must not be collateral damage.
 */
@Composable
internal fun SensitiveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    purpose: SensitiveFieldPurpose,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    format: SensitiveFieldFormat = SensitiveFieldFormat.TEXT,
    validation: LumeFieldValidation = LumeFieldValidation.Neutral,
    state: LumeFieldState = LumeFieldState.Enabled,
    submit: LumeFieldSubmit = LumeFieldSubmit.Done,
    /** A credential's show/hide control; `null` hides it. */
    reveal: LumeRevealLabels? = null,
    /** A password being CREATED: the platform offers a strong one instead of saving a half-typed secret. */
    newCredential: Boolean = false,
    onSubmit: (() -> Unit)? = null,
) {
    val request = kitFieldRequestFor(purpose, format)
    if (request.masked) {
        LumePasswordField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            modifier = modifier,
            reveal = reveal,
            newCredential = newCredential,
            state = state,
            validation = validation,
            submit = submit,
            onSubmit = onSubmit,
        )
    } else if (request.document) {
        LumeDocumentField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            modifier = modifier,
            format = LumeDocumentFormat.ChileanRut,
            state = state,
            validation = validation,
            submit = submit,
            onSubmit = onSubmit,
        )
    } else {
        LumeTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            modifier = modifier,
            keyboard = request.keyboard,
            textCase = request.textCase,
            state = state,
            validation = validation,
            submit = submit,
            onSubmit = onSubmit,
        )
    }
}
