package com.luismejias.lumemedlink.core.links

import com.luismejias.lumemedlink.shared.PushSignalKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeepLinkCatalogTest {
    /**
     * The published path of every entry, written by hand. An entry added to the catalog does not compile here until
     * its path is pinned, and a path that changes fails: links already sent in messages would stop opening.
     */
    private fun published(entry: DeepLinkEntry): String = when (entry) {
        DeepLinkEntry.HOME -> "inicio"
        DeepLinkEntry.SEARCH -> "buscar"
        DeepLinkEntry.APPOINTMENTS -> "citas"
        DeepLinkEntry.APPOINTMENT_DETAIL -> "citas/detalle"
        DeepLinkEntry.APPOINTMENT_RATING -> "citas/valorar"
        DeepLinkEntry.MY_DOCTORS -> "medicos"
        DeepLinkEntry.SETTINGS -> "configuracion"
        DeepLinkEntry.SETTINGS_PROFILE -> "configuracion/mis-datos"
        DeepLinkEntry.SETTINGS_REMINDERS -> "configuracion/recordatorios"
        DeepLinkEntry.SETTINGS_RATINGS -> "configuracion/valoraciones"
        DeepLinkEntry.SETTINGS_COMPLAINTS -> "configuracion/reclamos"
        DeepLinkEntry.SETTINGS_PRIVACY -> "configuracion/privacidad"
        DeepLinkEntry.NOTICES -> "avisos"
        DeepLinkEntry.NOTICE_DETAIL -> "avisos/detalle"
        DeepLinkEntry.DOCTOR_AGENDA -> "medico/agenda"
        DeepLinkEntry.DOCTOR_PATIENTS -> "medico/pacientes"
        DeepLinkEntry.DOCTOR_SETTINGS -> "medico/configuracion"
    }

    @Test
    fun everyPublishedPathIsPinned() {
        DeepLinkEntry.entries.forEach { assertEquals(published(it), it.path, it.name) }
    }

    @Test
    fun noTwoEntriesClaimTheSamePath() {
        assertEquals(DeepLinkEntry.entries.size, DeepLinkEntry.entries.map { it.path }.toSet().size)
    }

    @Test
    fun pathsAreLowercaseWordsWithoutValues() {
        val shape = Regex("^[a-z]+(-[a-z]+)*(/[a-z]+(-[a-z]+)*)*$")
        DeepLinkEntry.entries.forEach { assertTrue(shape.matches(it.path), "values go in the query: ${it.path}") }
    }

    @Test
    fun queryNamesAreDistinct() {
        assertEquals(LinkParam.entries.size, LinkParam.entries.map { it.queryName }.toSet().size)
    }

    @Test
    fun everySignalMayUseItsOwnDefault() {
        PushSignalKind.entries.forEach { kind ->
            kind.defaultEntry?.let { assertTrue(kind.allows(it), "$kind's default must be one it may open") }
        }
    }

    @Test
    fun aDefaultNeverNeedsAnIdItDoesNotHave() {
        PushSignalKind.entries.mapNotNull { it.defaultEntry }.forEach { assertEquals(null, it.requires, it.name) }
    }
}
