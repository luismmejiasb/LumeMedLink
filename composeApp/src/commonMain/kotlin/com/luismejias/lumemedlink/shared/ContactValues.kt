package com.luismejias.lumemedlink.shared

/**
 * A Chilean mobile number, E.164 (`+569XXXXXXXX`). The Chilean seam named as such (§0, multi-country): the
 * design's rule is "+56 9 and 8 digits". Accepts what a person types — spaces, the `+56` or not, the `9` —
 * and refuses anything that is not a mobile.
 *
 * `toString` never prints the number (§8.1).
 */
internal class ChileanMobile private constructor(val e164: String) {
    /** "+56 9 •••• 4321": the last four digits only, for "Te enviamos un código al …" (2c). */
    val masked: String get() = "+56 9 •••• ${e164.takeLast(MASK_TAIL)}"

    /** "+56 9 8765 4321": the whole number, for the owner's own profile (p7). */
    val display: String get() = "+56 9 ${e164.takeLast(SUBSCRIBER_DIGITS).chunked(MASK_TAIL).joinToString(" ")}"

    override fun equals(other: Any?): Boolean = other is ChileanMobile && other.e164 == e164

    override fun hashCode(): Int = e164.hashCode()

    override fun toString(): String = "ChileanMobile(redacted)"

    companion object {
        private const val COUNTRY = "56"
        private const val SUBSCRIBER_DIGITS = 8
        private const val MASK_TAIL = 4

        fun parse(typed: String): ChileanMobile? {
            val digits = typed.filter { it.isDigit() }
            val national = if (digits.startsWith(COUNTRY) && digits.length == SUBSCRIBER_DIGITS + 3) {
                digits.drop(COUNTRY.length)
            } else {
                digits
            }
            if (national.length != SUBSCRIBER_DIGITS + 1 || national.first() != '9') return null
            return ChileanMobile("+$COUNTRY$national")
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
