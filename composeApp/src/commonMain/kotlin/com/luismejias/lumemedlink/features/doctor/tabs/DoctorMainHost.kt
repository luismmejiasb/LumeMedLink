package com.luismejias.lumemedlink.features.doctor.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.lume.uicomposer.components.LocalLumeToastHostState
import cl.lume.uicomposer.components.LumeCountBadge
import cl.lume.uicomposer.components.LumeFloatingTabBarClearance
import cl.lume.uicomposer.components.LumeTabBar
import cl.lume.uicomposer.components.LumeTabBarItem
import cl.lume.uicomposer.components.LumeTabBarStyle
import cl.lume.uicomposer.components.LumeToastAppearance
import cl.lume.uicomposer.components.LumeToastHost
import cl.lume.uicomposer.components.rememberLumeToastHostState
import cl.lume.uicomposer.foundations.LumeIcon
import com.luismejias.lumemedlink.core.care.DoctorCareGateway
import com.luismejias.lumemedlink.core.device.rememberDeviceContact
import com.luismejias.lumemedlink.core.device.rememberPhotoPicker
import com.luismejias.lumemedlink.core.features.Feature
import com.luismejias.lumemedlink.core.features.FeatureGate
import com.luismejias.lumemedlink.core.links.DeepLink
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.core.screen.LocalTabBarClearance
import com.luismejias.lumemedlink.core.screen.ProfilePhotoFlow
import com.luismejias.lumemedlink.core.screen.ProfilePhotoModel
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
import com.luismejias.lumemedlink.resources.toast_dismiss
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal enum class DoctorTab { AGENDA, PATIENTS, SETTINGS }

/** The feature a tab shows (ADR-0060); `null` for «Configuración», which is always there. */
internal fun DoctorTab.feature(): Feature? = when (this) {
    DoctorTab.AGENDA -> Feature.DOCTOR_AGENDA
    DoctorTab.PATIENTS -> Feature.DOCTOR_PATIENTS
    DoctorTab.SETTINGS -> null
}

/** The tabs [available] lets the doctor see, in the design's order. «Configuración» is always among them. */
internal fun visibleDoctorTabs(available: Set<Feature>): List<DoctorTab> =
    DoctorTab.entries.filter { tab -> tab.feature()?.let { it in available } ?: true }

/** [selected] while it is visible; otherwise the first tab still shown. */
internal fun shownDoctorTab(selected: DoctorTab, visible: List<DoctorTab>): DoctorTab =
    if (selected in visible) selected else visible.first()

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
internal fun DoctorMainHost(
    care: DoctorCareGateway,
    /** What the doctor may see (ADR-0060). */
    features: FeatureGate,
    onSignOut: () -> Unit,
    /** What a link or a notification asks to open now (ADR-0053); [onLinkOpened] once it has. */
    link: DeepLink? = null,
    onLinkOpened: () -> Unit = {},
) {
    val tabs = viewModel { DoctorTabsViewModel() }
    val agenda = viewModel { AgendaViewModel(care) }
    val patients = viewModel { DoctorPatientsViewModel(care) }
    val profile = viewModel { DoctorProfileViewModel(care) }
    val photo = viewModel { ProfilePhotoModel() }
    val picker = rememberPhotoPicker(photo::picked)
    LaunchedEffect(link) {
        val opening = link ?: return@LaunchedEffect
        openDoctorLink(opening, tabs)
        onLinkOpened()
    }
    val toasts = rememberLumeToastHostState()
    val contact = rememberDeviceContact()
    val available by features.available.collectAsState()
    val visibleTabs = visibleDoctorTabs(available)
    val chosen by tabs.selected.collectAsState()
    val selected = shownDoctorTab(chosen, visibleTabs)
    val agendaLoad by agenda.state.collectAsState()
    val profileLoad by profile.state.collectAsState()
    val waiting = (agendaLoad as? CareLoad.Ready)?.content?.requests?.size ?: 0
    val doctor = (profileLoad as? CareLoad.Ready)?.content?.doctor

    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalTabBarClearance provides LumeFloatingTabBarClearance,
            LocalLumeToastHostState provides toasts,
        ) {
            when (selected) {
                DoctorTab.AGENDA -> AgendaScreen(agenda, doctor, onProfile = { tabs.show(DoctorTab.SETTINGS) })
                DoctorTab.PATIENTS -> DoctorPatientsScreen(patients, onCall = contact::dial, onMail = contact::mail)
                DoctorTab.SETTINGS -> DoctorProfileScreen(profile, photo, onSignOut)
            }
        }
        val agendaItem = LumeTabBarItem(
            LumeIcon.Calendar,
            stringResource(Res.string.doctor_tab_agenda),
            badge = LumeCountBadge(
                waiting,
                pluralStringResource(Res.plurals.doctor_requests_badge, waiting, waiting),
            ),
        )
        val patientsItem = LumeTabBarItem(LumeIcon.People, stringResource(Res.string.doctor_tab_patients))
        val settingsItem = LumeTabBarItem(LumeIcon.Gear, stringResource(Res.string.doctor_tab_settings))
        LumeTabBar(
            items = visibleTabs.map { tab ->
                when (tab) {
                    DoctorTab.AGENDA -> agendaItem
                    DoctorTab.PATIENTS -> patientsItem
                    DoctorTab.SETTINGS -> settingsItem
                }
            },
            selected = visibleTabs.indexOf(selected),
            onSelect = { tabs.show(visibleTabs[it]) },
            modifier = Modifier.align(Alignment.BottomCenter),
            style = LumeTabBarStyle.Floating,
        )
        ProfilePhotoFlow(photo, picker, toasts)
        // Above the floating bar, as k3 draws it, never over it.
        LumeToastHost(
            toasts,
            dismissLabel = stringResource(Res.string.toast_dismiss),
            modifier = Modifier.padding(bottom = LumeFloatingTabBarClearance),
            appearance = LumeToastAppearance.Inverted,
        )
    }
}
