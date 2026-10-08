package com.luismejias.lumemedlink.shared

/**
 * A Chilean RUT whose check digit is right (módulo 11). The Chilean seam named as such (§0, multi-country:
 * the country is the tenant's datum, and what is Chilean lives in seams that say so) — the sign-in asks
 * for it because the family's identity is keyed on it, as in LumeMed.
 *
 * `toString` never prints the number: a RUT plus a name is full personal data (Ley 21.719), and a log
 * line or a crash message that interpolates this type must not carry it (§8.1).
 */
internal class ChileanRut private constructor(val body: String, val checkDigit: Char) {
    /** The canonical form the backend receives: body, hyphen, check digit, no dots. */
    val canonical: String get() = "$body-$checkDigit"

    override fun equals(other: Any?): Boolean = other is ChileanRut && other.canonical == canonical

    override fun hashCode(): Int = canonical.hashCode()

    override fun toString(): String = "ChileanRut(redacted)"

    companion object {
        private const val MIN_BODY = 7
        private const val MAX_BODY = 8

        /** Parses what a person typed — dots, hyphen, spaces, a lowercase k — or `null` if it is not a valid RUT. */
        fun parse(typed: String): ChileanRut? {
            val clean = typed.filter { it.isLetterOrDigit() }.uppercase()
            if (clean.length < MIN_BODY + 1) return null
            val body = clean.dropLast(1)
            val digit = clean.last()
            if (body.length > MAX_BODY || !body.all { it.isDigit() }) return null
            if (!digit.isDigit() && digit != 'K') return null
            val trimmed = body.trimStart('0')
            if (trimmed.isEmpty()) return null
            return if (checkDigitFor(trimmed) == digit) ChileanRut(trimmed, digit) else null
        }

        /** Módulo 11 with the 2..7 weight cycle, from the right. */
        internal fun checkDigitFor(body: String): Char {
            var weight = 2
            var sum = 0
            for (c in body.reversed()) {
                sum += (c - '0') * weight
                weight = if (weight == 7) 2 else weight + 1
            }
            return when (val rest = 11 - sum % 11) {
                11 -> '0'
                10 -> 'K'
                else -> '0' + rest
            }
        }
    }
}
