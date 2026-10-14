package com.luismejias.lumemedlink.features.patient

import com.luismejias.lumemedlink.core.features.Feature
import com.luismejias.lumemedlink.features.patient.tabs.PatientTab
import com.luismejias.lumemedlink.features.patient.tabs.shownPatientTab
import com.luismejias.lumemedlink.features.patient.tabs.visiblePatientTabs
import kotlin.test.Test
import kotlin.test.assertEquals

/** Which of the patient's tabs `FeatureGate` leaves on screen (ADR-0060): «Inicio» and «Configuración» always. */
class PatientTabGateTest {
    @Test
    fun withEveryFeatureThePatientSeesTheFiveTabsInTheDesignsOrder() {
        assertEquals(PatientTab.entries, visiblePatientTabs(Feature.entries.toSet()))
    }

    @Test
    fun withNothingAvailableThePatientKeepsHomeAndSettings() {
        assertEquals(listOf(PatientTab.HOME, PatientTab.SETTINGS), visiblePatientTabs(emptySet()))
    }

    @Test
    fun aPatientOnAHiddenTabIsShownHome() {
        val visible = visiblePatientTabs(emptySet())
        assertEquals(PatientTab.HOME, shownPatientTab(PatientTab.APPOINTMENTS, visible))
        assertEquals(PatientTab.SETTINGS, shownPatientTab(PatientTab.SETTINGS, visible))
    }

    @Test
    fun aSwitchedOffSearchTakesItsTabAwayAndNothingElse() {
        val visible = visiblePatientTabs(Feature.entries.toSet() - Feature.DOCTOR_SEARCH)
        assertEquals(
            listOf(PatientTab.HOME, PatientTab.APPOINTMENTS, PatientTab.DOCTORS, PatientTab.SETTINGS),
            visible,
        )
    }
}
