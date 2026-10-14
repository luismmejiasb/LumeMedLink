package com.luismejias.lumemedlink.features.doctor

import com.luismejias.lumemedlink.core.features.Feature
import com.luismejias.lumemedlink.features.doctor.tabs.DoctorActions
import com.luismejias.lumemedlink.features.doctor.tabs.DoctorTab
import com.luismejias.lumemedlink.features.doctor.tabs.doctorActions
import com.luismejias.lumemedlink.features.doctor.tabs.shownDoctorTab
import com.luismejias.lumemedlink.features.doctor.tabs.visibleDoctorTabs
import kotlin.test.Test
import kotlin.test.assertEquals

/** Which of the doctor's tabs `FeatureGate` leaves on screen (ADR-0060): «Configuración» always. */
class DoctorTabGateTest {
    @Test
    fun withEveryFeatureTheDoctorSeesTheThreeTabs() {
        assertEquals(DoctorTab.entries, visibleDoctorTabs(Feature.entries.toSet()))
    }

    @Test
    fun withNothingAvailableTheDoctorKeepsSettingsAndLandsThere() {
        val visible = visibleDoctorTabs(emptySet())
        assertEquals(listOf(DoctorTab.SETTINGS), visible)
        assertEquals(DoctorTab.SETTINGS, shownDoctorTab(DoctorTab.AGENDA, visible))
    }

    @Test
    fun aDoctorWhoseAgendaIsOffLandsOnTheFirstTabStillShown() {
        val visible = visibleDoctorTabs(Feature.entries.toSet() - Feature.DOCTOR_AGENDA)
        assertEquals(DoctorTab.PATIENTS, shownDoctorTab(DoctorTab.AGENDA, visible))
    }

    @Test
    fun theRequestsAndTheSharingSwitchFollowTheirOwnFeatures() {
        val all = Feature.entries.toSet()
        assertEquals(DoctorActions(answerRequests = true, shareContact = true), doctorActions(all))
        assertEquals(DoctorActions(answerRequests = false, shareContact = false), doctorActions(emptySet()))
        assertEquals(DoctorActions(false, true), doctorActions(all - Feature.DOCTOR_REQUESTS))
        assertEquals(DoctorActions(true, false), doctorActions(all - Feature.DOCTOR_CONTACT_SHARING))
    }
}
