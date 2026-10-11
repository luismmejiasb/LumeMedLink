package com.luismejias.lumemedlink.features.patient.parts

import cl.lume.uicomposer.components.LumeTextRun

private val ACCENTS = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n')

/** For matching what a person types: trimmed, lower case, without accents — "rendon" finds "Rendón". */
internal fun String.folded(): String = trim().lowercase().map { ACCENTS[it] ?: it }.joinToString("")

/**
 * A resolved sentence whose `**…**` stretches the design draws stressed — «La Dra. Rendón **confirma cada reserva**» —
 * as the kit's runs. Each language marks its own stress, so a translation is never cut into fragments.
 */
internal fun String.strongRuns(): List<LumeTextRun> = split("**").mapIndexedNotNull { index, part ->
    when {
        part.isEmpty() -> null
        index % 2 == 1 -> LumeTextRun.strong(part)
        else -> LumeTextRun.plain(part)
    }
}
