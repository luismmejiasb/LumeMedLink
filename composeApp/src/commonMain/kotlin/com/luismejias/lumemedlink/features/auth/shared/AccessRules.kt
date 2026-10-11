package com.luismejias.lumemedlink.features.auth.shared

import com.luismejias.lumemedlink.shared.ChileanRut

/** A RUT is judged only once it is long enough to be one: no red while the person is still typing it. */
private const val RUT_JUDGED_FROM = 8
private const val PASSWORD_MIN_LENGTH = 8

/**
 * Whether two typed values are the same valid RUT. The kit's RUT field reformats what it holds ("22222222-2" →
 * "22.222.222-2") and reports that as a change; a state that a new RUT should clear must survive a reformat.
 */
internal fun sameRut(a: String, b: String): Boolean = ChileanRut.parse(a)?.let { it == ChileanRut.parse(b) } ?: false

/** `true` when a typed RUT should show its error. */
internal fun rutLooksWrong(typed: String): Boolean =
    typed.count { it.isLetterOrDigit() } >= RUT_JUDGED_FROM && ChileanRut.parse(typed) == null

/**
 * The design's password rules, checked live (2b, 3c). They are the DESIGN's (ADR-0044): wired to an IdP, they
 * must equal its policy or go — a rule shown here and not enforced there is a promise nobody keeps.
 */
internal data class PasswordRules(val longEnough: Boolean, val mixedCase: Boolean, val hasDigit: Boolean) {
    val allMet: Boolean get() = longEnough && mixedCase && hasDigit

    companion object {
        fun of(password: String) = PasswordRules(
            longEnough = password.length >= PASSWORD_MIN_LENGTH,
            mixedCase = password.any { it.isUpperCase() } && password.any { it.isLowerCase() },
            hasDigit = password.any { it.isDigit() },
        )
    }
}

/** A new password, typed twice. Never prints either (§8.1). */
internal data class NewPasswordEntry(val password: String = "", val repeated: String = "") {
    val rules: PasswordRules get() = PasswordRules.of(password)

    /** The second entry differs — judged only once it has something in it. */
    val mismatch: Boolean get() = repeated.isNotEmpty() && repeated != password

    val matches: Boolean get() = repeated.isNotEmpty() && repeated == password

    val acceptable: Boolean get() = rules.allMet && matches

    override fun toString(): String = "NewPasswordEntry(redacted)"
}

/** A one-time code step (2c, 3b): what was typed and how the last try went. */
internal data class CodeEntry(
    val code: String = "",
    /** After a wrong code: how many tries are left. */
    val wrongAttemptsLeft: Int? = null,
    /** 3b's inline answer to a code that lapsed; 2c has its own screen for it. */
    val expired: Boolean = false,
    val offline: Boolean = false,
) {
    val complete: Boolean get() = code.length == CODE_LENGTH

    override fun toString(): String = "CodeEntry(redacted)"

    companion object {
        const val CODE_LENGTH = 6
    }
}
