package com.luismejias.lumemedlink.features.patient.parts

private val ACCENTS = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n')

/** For matching what a person types: trimmed, lower case, without accents — "rendon" finds "Rendón". */
internal fun String.folded(): String = trim().lowercase().map { ACCENTS[it] ?: it }.joinToString("")
