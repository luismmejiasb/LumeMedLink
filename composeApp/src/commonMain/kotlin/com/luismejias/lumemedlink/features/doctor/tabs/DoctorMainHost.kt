package com.luismejias.lumemedlink.features.doctor.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.lume.uicomposer.components.LumeCountBadge
import cl.lume.uicomposer.components.LumeFloatingTabBarClearance
import cl.lume.uicomposer.components.LumeTabBar
import cl.lume.uicomposer.components.LumeTabBarItem
import cl.lume.uicomposer.components.LumeTabBarStyle
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.core.care.DoctorCareGateway
import com.luismejias.lumemedlink.core.device.rememberDeviceContact
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.core.screen.LocalTabBarClearance
import com.luismejias.lumemedlink.features.doctor.agenda.AgendaScreen
import com.luismejias.lumemedlink.features.doctor.agenda.AgendaViewModel
import com.luismejias.lumemedlink.features.doctor.patients.DoctorPatientsScreen
import com.luismejias.lumemedlink.features.doctor.patients.DoctorPatientsViewModel
import com.luismejias.lumemedlink.features.doctor.profile.DoctorProfileScreen
import com.luismejias.lumemedlink.features.doctor.profile.DoctorProfileViewModel
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.doctor_requests_badge
import com.luismejias.lumemedlink.resources.doctor_tab_agenda
import com.luismejias.lumemedlink.resources.doctor_tab_patients
import com.luismejias.lumemedlink.resources.doctor_tab_settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal enum class DoctorTab { AGENDA, PATIENTS, SETTINGS }

/** Which of the doctor's three sections is in front (ADR-0045: one stack per tab, each only its root today). */
internal class DoctorTabsViewModel : ViewModel() {
    private val current = MutableStateFlow(DoctorTab.AGENDA)
    val selected: StateFlow<DoctorTab> = current.asStateFlow()

    fun show(tab: DoctorTab) {
        current.value = tab
    }
}

/**
 * The doctor's main app (m1–m3) over the kit's floating tab bar. Every ViewModel lives in the caller's store, which a
 * sign-out empties (app/SessionScope, §8.13).
 */
@Composable
internal fun DoctorMainHost(care: DoctorCareGateway, onSignOut: () -> Unit) {
    val tabs = viewModel { DoctorTabsViewModel() }
    val agenda = viewModel { AgendaViewModel(care) }
    val patients = viewModel { DoctorPatientsViewModel(care) }
    val profile = viewModel { DoctorProfileViewModel(care) }
    val contact = rememberDeviceContact()
    val selected by tabs.selected.collectAsState()
    val agendaLoad by agenda.state.collectAsState()
    val profileLoad by profile.state.collectAsState()
    val waiting = (agendaLoad as? CareLoad.Ready)?.content?.requests?.size ?: 0
    val doctor = (profileLoad as? CareLoad.Ready)?.content?.doctor

    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalTabBarClearance provides LumeFloatingTabBarClearance) {
            when (selected) {
                DoctorTab.AGENDA -> AgendaScreen(agenda, doctor, onProfile = { tabs.show(DoctorTab.SETTINGS) })
                DoctorTab.PATIENTS -> DoctorPatientsScreen(patients, onCall = contact::dial, onMail = contact::mail)
                DoctorTab.SETTINGS -> DoctorProfileScreen(profile, onSignOut)
            }
        }
        LumeTabBar(
            items = listOf(
                LumeTabBarItem(
                    LumeIcon.Calendar,
                    stringResource(Res.string.doctor_tab_agenda),
                    badge = LumeCountBadge(
                        waiting,
                        pluralStringResource(Res.plurals.doctor_requests_badge, waiting, waiting),
                    ),
                ),
                LumeTabBarItem(LumeIcon.People, stringResource(Res.string.doctor_tab_patients)),
                LumeTabBarItem(LumeIcon.Gear, stringResource(Res.string.doctor_tab_settings)),
            ),
            selected = selected.ordinal,
            onSelect = { tabs.show(DoctorTab.entries[it]) },
            modifier = Modifier.align(Alignment.BottomCenter),
            style = LumeTabBarStyle.Floating,
        )
    }
}
