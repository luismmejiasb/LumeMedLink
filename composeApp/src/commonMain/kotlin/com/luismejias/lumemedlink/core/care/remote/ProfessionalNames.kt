package com.luismejias.lumemedlink.core.care.remote

/**
 * A professional as the design names them: «Dra. Ana Sintética», the title the contract gives, then the given name and
 * the first family name. Shared by the directory and «Mis médicos», so one doctor reads the same on both.
 */
internal fun professionalDisplayName(title: String?, givenName: String, familyName: String): String =
    listOfNotNull(title, givenName, familyName).filter { it.isNotBlank() }.joinToString(" ")
