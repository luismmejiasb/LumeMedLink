package com.luismejias.lumemedlink.features.patient.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.lume.uicomposer.components.LumeCountBadge
import cl.lume.uicomposer.components.LumeTabBar
import cl.lume.uicomposer.components.LumeTabBarItem
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.core.care.PatientCareGateway
import com.luismejias.lumemedlink.core.device.rememberDeviceContact
import com.luismejias.lumemedlink.features.patient.appointments.AppointmentsScreen
import com.luismejias.lumemedlink.features.patient.appointments.AppointmentsViewModel
import com.luismejias.lumemedlink.features.patient.doctors.MyDoctorsScreen
import com.luismejias.lumemedlink.features.patient.doctors.MyDoctorsViewModel
import com.luismejias.lumemedlink.features.patient.home.HomeScreen
import com.luismejias.lumemedlink.features.patient.home.HomeViewModel
import com.luismejias.lumemedlink.features.patient.parts.CareLoad
import com.luismejias.lumemedlink.features.patient.search.SearchScreen
import com.luismejias.lumemedlink.features.patient.search.SearchViewModel
import com.luismejias.lumemedlink.features.patient.settings.SettingsEntry
import com.luismejias.lumemedlink.features.patient.settings.SettingsScreen
import com.luismejias.lumemedlink.features.patient.settings.SettingsViewModel
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.tab_appointments
import com.luismejias.lumemedlink.resources.tab_doctors
import com.luismejias.lumemedlink.resources.tab_home
import com.luismejias.lumemedlink.resources.tab_pending_badge
import com.luismejias.lumemedlink.resources.tab_search
import com.luismejias.lumemedlink.resources.tab_settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The patient's five sections, in the design's order (p1, p2, p5, p6, p7). */
internal enum class PatientTab { HOME, SEARCH, APPOINTMENTS, DOCTORS, SETTINGS }

/**
 * Which section is in front (ADR-0045: one stack per tab over LumeTabBar). Each tab is only its root today;
 * the stacks arrive with the flows that push onto them (search → booking, an appointment's detail).
 */
internal class PatientTabsViewModel : ViewModel() {
    private val current = MutableStateFlow(PatientTab.HOME)
    val selected: StateFlow<PatientTab> = current.asStateFlow()

    fun show(tab: PatientTab) {
        current.value = tab
    }
}

/**
 * The patient's main app: the five tab roots over the kit's tab bar. Every ViewModel here is created in the
 * caller's store — the composition root gives it one that dies on sign-out (app/SessionScope).
 */
@Composable
internal fun PatientMainHost(care: PatientCareGateway, onSignOut: () -> Unit) {
    val tabs = viewModel { PatientTabsViewModel() }
    val home = viewModel { HomeViewModel(care) }
    val search = viewModel { SearchViewModel(care) }
    val appointments = viewModel { AppointmentsViewModel(care) }
    val doctors = viewModel { MyDoctorsViewModel(care) }
    val settings = viewModel { SettingsViewModel(care) }
    val contact = rememberDeviceContact()
    val selected by tabs.selected.collectAsState()
    val appointmentsLoad by appointments.state.collectAsState()
    val awaiting = (appointmentsLoad as? CareLoad.Ready)?.content?.awaiting ?: 0

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (selected) {
                PatientTab.HOME -> HomeScreen(
                    model = home,
                    onDirections = {},
                    onChangeOrCancel = {},
                    onOpenAppointment = {},
                    onRate = {},
                    onSearch = { tabs.show(PatientTab.SEARCH) },
                )
                PatientTab.SEARCH -> SearchScreen(
                    model = search,
                    onPickSpecialty = {},
                    onPickCommune = {},
                    onSearch = {},
                    onSearchByName = {},
                )
                PatientTab.APPOINTMENTS -> AppointmentsScreen(
                    model = appointments,
                    onOpen = {},
                    onChange = {},
                    onCancel = {},
                    onSearch = { tabs.show(PatientTab.SEARCH) },
                )
                PatientTab.DOCTORS -> MyDoctorsScreen(
                    model = doctors,
                    onOpen = {},
                    onCall = contact::dial,
                    onSearch = { tabs.show(PatientTab.SEARCH) },
                )
                PatientTab.SETTINGS -> SettingsScreen(
                    model = settings,
                    onEditPhone = {},
                    onEditEmail = {},
                    onEntry = { if (it == SettingsEntry.SIGN_OUT) onSignOut() },
                )
            }
        }
        LumeTabBar(
            items = listOf(
                LumeTabBarItem(LumeIcon.Home, stringResource(Res.string.tab_home)),
                LumeTabBarItem(LumeIcon.Search, stringResource(Res.string.tab_search)),
                LumeTabBarItem(
                    LumeIcon.Calendar,
                    stringResource(Res.string.tab_appointments),
                    badge = LumeCountBadge(
                        awaiting,
                        pluralStringResource(Res.plurals.tab_pending_badge, awaiting, awaiting),
                    ),
                ),
                LumeTabBarItem(LumeIcon.Doctors, stringResource(Res.string.tab_doctors)),
                LumeTabBarItem(LumeIcon.Gear, stringResource(Res.string.tab_settings)),
            ),
            selected = selected.ordinal,
            onSelect = { tabs.show(PatientTab.entries[it]) },
        )
    }
}
