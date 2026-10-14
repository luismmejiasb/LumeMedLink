package com.luismejias.lumemedlink.features.patient

import com.luismejias.lumemedlink.core.features.Feature
import com.luismejias.lumemedlink.features.patient.tabs.PatientActions
import com.luismejias.lumemedlink.features.patient.tabs.PatientTab
import com.luismejias.lumemedlink.features.patient.tabs.onlyIf
import com.luismejias.lumemedlink.features.patient.tabs.patientActions
import com.luismejias.lumemedlink.features.patient.tabs.shownPatientTab
import com.luismejias.lumemedlink.features.patient.tabs.visiblePatientTabs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun withEveryFeatureEveryActionInsideTheScreensIsThere() {
        assertEquals(PatientActions(true, true, true, true, true, true), patientActions(Feature.entries.toSet()))
    }

    @Test
    fun withNothingAvailableNoActionHasADoor() {
        assertEquals(PatientActions(false, false, false, false, false, false), patientActions(emptySet()))
    }

    @Test
    fun eachActionFollowsItsOwnFeatureAlone() {
        val all = Feature.entries.toSet()
        assertEquals(false, patientActions(all - Feature.APPOINTMENT_CHANGES).changeAppointments)
        assertEquals(false, patientActions(all - Feature.RATINGS).rate)
        assertEquals(false, patientActions(all - Feature.NOTICES).notices)
        assertEquals(false, patientActions(all - Feature.CONTACT_CHANGE).changeContact)
        assertEquals(
            PatientActions(true, true, false, true, true, true),
            patientActions(all - Feature.BOOKING),
            "booking off leaves the directory and everything else",
        )
    }

    @Test
    fun bookingGoesThroughTheDirectorySoSearchOffTakesBookingWithIt() {
        val actions = patientActions(Feature.entries.toSet() - Feature.DOCTOR_SEARCH)
        assertEquals(false to false, actions.search to actions.book)
    }

    @Test
    fun anActionThatIsOffIsNoCallbackAtAll() {
        val open: () -> Unit = {}
        assertNull(open.onlyIf(false))
        assertEquals(open, open.onlyIf(true))
    }
}
