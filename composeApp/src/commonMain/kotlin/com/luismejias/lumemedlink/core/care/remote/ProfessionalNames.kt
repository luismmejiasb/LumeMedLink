package com.luismejias.lumemedlink.core.care.remote

/**
 * A professional's name as the app shows it: the given name and the first family name. Shared by the directory and «Mis
 * médicos», so one doctor reads the same on both.
 *
 * The contract's `professionalTitle` is the profession («Médico Cirujano»), not a form of address («Dra.»), so it never
 * goes before the name (the platform, 2026-10-14). The platform keeps no form of address at all.
 */
internal fun professionalDisplayName(givenName: String, familyName: String): String =
    listOf(givenName, familyName).filter { it.isNotBlank() }.joinToString(" ")
