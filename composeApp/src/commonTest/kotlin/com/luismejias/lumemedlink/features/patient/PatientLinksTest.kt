package com.luismejias.lumemedlink.features.patient

import com.luismejias.lumemedlink.core.care.ScriptedPatientCare
import com.luismejias.lumemedlink.core.links.DeepLink
import com.luismejias.lumemedlink.core.links.DeepLinkEntry
import com.luismejias.lumemedlink.features.patient.change.ChangeRoute
import com.luismejias.lumemedlink.features.patient.change.ChangeViewModel
import com.luismejias.lumemedlink.features.patient.change.changeSourcesOf
import com.luismejias.lumemedlink.features.patient.rating.RateViewModel
import com.luismejias.lumemedlink.features.patient.tabs.PatientTab
import com.luismejias.lumemedlink.features.patient.tabs.PatientTabsViewModel
import com.luismejias.lumemedlink.features.patient.tabs.openPatientLink
import com.luismejias.lumemedlink.shared.OpaqueRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// All data below is synthetic (§9).
@OptIn(ExperimentalCoroutinesApi::class)
class PatientLinksTest {
    private val care = ScriptedPatientCare(latency = {})
    private val tabs = PatientTabsViewModel()
    private val change = ChangeViewModel(changeSourcesOf(care, care))
    private val rate = RateViewModel(care)

    @BeforeTest
    fun mainDispatcher() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun resetDispatcher() = Dispatchers.resetMain()

    private fun detailOf(id: String) = DeepLink(DeepLinkEntry.APPOINTMENT_DETAIL, OpaqueRef.parseOrNull(id))

    @Test
    fun aDetailLinkOpensThePatientsOwnAppointment() = runTest {
        openPatientLink(detailOf("a-rendon-0902"), tabs, change, rate)
        assertEquals(PatientTab.APPOINTMENTS, tabs.selected.value)
        assertEquals(listOf(ChangeRoute.DETAIL), change.routes.value)
    }

    @Test
    fun anIdThatIsNotTheirsOpensNothing() = runTest {
        // A link grants navigation, never access: someone else's id lands on Mis citas and no further.
        openPatientLink(detailOf("a-someone-else-01"), tabs, change, rate)
        assertEquals(PatientTab.APPOINTMENTS, tabs.selected.value)
        assertTrue(change.routes.value.isEmpty())
    }

    @Test
    fun aLinkClosesWhatWasOpenBeforeLanding() = runTest {
        openPatientLink(detailOf("a-rendon-0902"), tabs, change, rate)
        openPatientLink(DeepLink(DeepLinkEntry.SETTINGS_REMINDERS), tabs, change, rate)
        assertEquals(PatientTab.SETTINGS, tabs.selected.value)
        assertTrue(change.routes.value.isEmpty(), "the detail must not stay on top of Configuración")
    }

    @Test
    fun theNoticeCentreLandsOnInicioUntilItIsDesigned() = runTest {
        tabs.show(PatientTab.DOCTORS)
        openPatientLink(DeepLink(DeepLinkEntry.NOTICES), tabs, change, rate)
        assertEquals(PatientTab.HOME, tabs.selected.value)
    }
}
