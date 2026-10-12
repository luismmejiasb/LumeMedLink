package com.luismejias.lumemedlink.core.screen

import cl.lume.uicomposer.components.LumeTextRun

private val ACCENTS = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n')

/** For matching what a person types: trimmed, lower case, without accents — "rendon" finds "Rendón". */
internal fun String.folded(): String = trim().lowercase().map { ACCENTS[it] ?: it }.joinToString("")

/**
 * A resolved sentence as the kit's runs: `**…**` the stretches the design draws stressed («La Dra. Rendón **confirma
 * cada reserva**»), and `[[…]]` a link whose words are its name («mejor [[cámbiala]]»), which runs [onLink]. Each
 * language marks its own, so a translation is never cut into fragments.
 */
internal fun String.richRuns(onLink: () -> Unit = {}): List<LumeTextRun> {
    val runs = mutableListOf<LumeTextRun>()
    var at = 0
    MARKS.findAll(this).forEach { mark ->
        if (mark.range.first > at) runs += LumeTextRun.plain(substring(at, mark.range.first))
        val strong = mark.groups[1]?.value
        runs += if (strong != null) LumeTextRun.strong(strong) else LumeTextRun.link(mark.groupValues[2], onLink)
        at = mark.range.last + 1
    }
    if (at < length) runs += LumeTextRun.plain(substring(at))
    return runs
}

/** [richRuns] for a sentence with stress and no link. */
internal fun String.strongRuns(): List<LumeTextRun> = richRuns()

private val MARKS = Regex("""\*\*(.+?)\*\*|\[\[(.+?)]]""")
