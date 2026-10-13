package com.luismejias.lumemedlink.shared

/**
 * A phone number, E.164, with the dial code of the country it was typed for. Chile is the named seam (§0,
 * multi-country): a Chilean number must be a mobile, the design's "+56 9 and 8 digits", grouped «9 8765 4321».
 * Any other country is taken by E.164's length alone (the author, 2026-10-13): its rules are its own and not this
 * app's to guess, so it is shown ungrouped.
 *
 * `toString` never prints the number (§8.1).
 */
internal class PhoneNumber private constructor(val e164: String, private val dialCode: String) {
    private val national: String get() = e164.drop(dialCode.length)

    private val chilean: Boolean get() = dialCode == CHILE

    /**
     * "+56 9 •••• 4321": the last four digits only, for "Te enviamos un código al …" (2c). Its spaces do not break, so
     * a sentence never splits the number across two lines (seen on the emulator, 2026-10-13).
     */
    val masked: String
        get() {
            val tail = e164.takeLast(MASK_TAIL)
            val parts = if (chilean) listOf(CHILE, "9", "••••", tail) else listOf(dialCode, "••••", tail)
            return parts.joinToString(NO_BREAK)
        }

    /** "+56 9 8765 4321": the whole number, for the owner's own profile (p7). Kept on one line, as [masked]. */
    val display: String
        get() = if (chilean) {
            (listOf(CHILE, "9") + national.drop(1).chunked(MASK_TAIL)).joinToString(NO_BREAK)
        } else {
            dialCode + NO_BREAK + national
        }

    override fun equals(other: Any?): Boolean = other is PhoneNumber && other.e164 == e164

    override fun hashCode(): Int = e164.hashCode()

    override fun toString(): String = "PhoneNumber(redacted)"

    companion object {
        /** Chile's dial code: the country the sign-up starts on. */
        const val CHILE = "+56"

        /** How a Chilean mobile is drawn as it is typed: «9 8765 4321». */
        val CHILEAN_MOBILE_GROUPS: List<Int> = listOf(1, 4, 4)

        /** A space a line never breaks at. */
        const val NO_BREAK = "\u00A0"

        private const val CHILE_DIGITS = "56"
        private const val SUBSCRIBER_DIGITS = 8
        private const val MASK_TAIL = 4

        /** E.164's ceiling: fifteen digits, the country's included. */
        private const val E164_DIGITS = 15

        /** The shortest national number any country uses. */
        private const val SHORTEST_NATIONAL = 4

        /**
         * A Chilean mobile from what a person types — spaces, the `+56` or not, the `9` — or `null` for anything
         * that is not one.
         */
        fun chileanMobile(typed: String): PhoneNumber? {
            val digits = typed.filter { it.isDigit() }
            val national = if (digits.startsWith(CHILE_DIGITS) && digits.length == SUBSCRIBER_DIGITS + 3) {
                digits.drop(CHILE_DIGITS.length)
            } else {
                digits
            }
            if (national.length != SUBSCRIBER_DIGITS + 1 || national.first() != '9') return null
            return PhoneNumber("$CHILE$national", CHILE)
        }

        /**
         * A national number typed for the country whose dial code is [dialCode] («+54»). Chile keeps its mobile
         * rule; another country only E.164's length. `null` when the dial code is not one.
         */
        fun of(dialCode: String, national: String): PhoneNumber? {
            if (dialCode == CHILE) return chileanMobile(national)
            val code = dialCode.removePrefix("+")
            if (code.isEmpty() || !code.all { it.isDigit() }) return null
            val digits = national.filter { it.isDigit() }
            if (digits.length < SHORTEST_NATIONAL || code.length + digits.length > E164_DIGITS) return null
            return PhoneNumber("+$code$digits", "+$code")
        }
    }
}

/**
 * An email address of the shape `nombre@dominio` (the design's rule). Only the shape: whether the mailbox
 * exists is not a client's question. `toString` never prints it (§8.1).
 */
internal class EmailAddress private constructor(val value: String) {
    override fun equals(other: Any?): Boolean = other is EmailAddress && other.value == value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "EmailAddress(redacted)"

    companion object {
        private val SHAPE = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        fun parse(typed: String): EmailAddress? = typed.trim().takeIf { SHAPE.matches(it) }?.let(::EmailAddress)
    }
}
