package com.luismejias.lumemedlink.features.patient.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import cl.lume.uicomposer.tokens.LumeStatusStyle
import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.DirectoryGateway
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.PatientCareGateway
import com.luismejias.lumemedlink.core.care.RatingPrompt
import com.luismejias.lumemedlink.core.device.rememberAddressClipboard
import com.luismejias.lumemedlink.core.device.rememberDeviceContact
import com.luismejias.lumemedlink.core.device.rememberDirections
import com.luismejias.lumemedlink.core.device.rememberPhotoPicker
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.core.screen.LocalTabBarClearance
import com.luismejias.lumemedlink.core.screen.ProfilePhotoFlow
import com.luismejias.lumemedlink.core.screen.ProfilePhotoModel
import com.luismejias.lumemedlink.core.telemetry.AnalyticsEvent
import com.luismejias.lumemedlink.core.telemetry.TelemetryConsent
import com.luismejias.lumemedlink.features.patient.appointments.AppointmentsScreen
import com.luismejias.lumemedlink.features.patient.appointments.AppointmentsSegment
import com.luismejias.lumemedlink.features.patient.appointments.AppointmentsViewModel
import com.luismejias.lumemedlink.features.patient.change.AppointmentDetailScreen
import com.luismejias.lumemedlink.features.patient.change.CancelScreen
import com.luismejias.lumemedlink.features.patient.change.ChangeDone
import com.luismejias.lumemedlink.features.patient.change.ChangeRoute
import com.luismejias.lumemedlink.features.patient.change.ChangeSheet
import com.luismejias.lumemedlink.features.patient.change.ChangeViewModel
import com.luismejias.lumemedlink.features.patient.change.DirectionsSheet
import com.luismejias.lumemedlink.features.patient.change.OptionsSheet
import com.luismejias.lumemedlink.features.patient.change.RescheduleScreen
import com.luismejias.lumemedlink.features.patient.doctors.MyDoctorsScreen
import com.luismejias.lumemedlink.features.patient.doctors.MyDoctorsViewModel
import com.luismejias.lumemedlink.features.patient.home.HomeScreen
import com.luismejias.lumemedlink.features.patient.home.HomeViewModel
import com.luismejias.lumemedlink.features.patient.rating.RateSheet
import com.luismejias.lumemedlink.features.patient.rating.RateViewModel
import com.luismejias.lumemedlink.features.patient.search.SearchHost
import com.luismejias.lumemedlink.features.patient.search.SearchViewModel
import com.luismejias.lumemedlink.features.patient.settings.SettingsEntry
import com.luismejias.lumemedlink.features.patient.settings.SettingsScreen
import com.luismejias.lumemedlink.features.patient.settings.SettingsViewModel
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.booked_toast
import com.luismejias.lumemedlink.resources.cancelled_toast
import com.luismejias.lumemedlink.resources.directions_copied
import com.luismejias.lumemedlink.resources.moved_toast
import com.luismejias.lumemedlink.resources.rate_sent_toast
import com.luismejias.lumemedlink.resources.sent_toast
import com.luismejias.lumemedlink.resources.tab_appointments
import com.luismejias.lumemedlink.resources.tab_doctors
import com.luismejias.lumemedlink.resources.tab_home
import com.luismejias.lumemedlink.resources.tab_pending_badge
import com.luismejias.lumemedlink.resources.tab_search
import com.luismejias.lumemedlink.resources.tab_settings
import com.luismejias.lumemedlink.resources.toast_dismiss
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
internal fun PatientMainHost(
    care: PatientCareGateway,
    directory: DirectoryGateway,
    consent: TelemetryConsent,
    logEvent: (AnalyticsEvent) -> Unit,
    onSignOut: () -> Unit,
) {
    val tabs = viewModel { PatientTabsViewModel() }
    val home = viewModel { HomeViewModel(care) }
    val search = viewModel { SearchViewModel(care, directory, logEvent) }
    val appointments = viewModel { AppointmentsViewModel(care) }
    val doctors = viewModel { MyDoctorsViewModel(care) }
    val settings = viewModel { SettingsViewModel(care, consent) }
    val change = viewModel { ChangeViewModel(care, directory, logEvent) }
    val rate = viewModel { RateViewModel(care, logEvent) }
    val photo = viewModel { ProfilePhotoModel() }
    val picker = rememberPhotoPicker(photo::picked)
    val contact = rememberDeviceContact()
    val directions = rememberDirections()
    val clipboard = rememberAddressClipboard()
    val changeRoutes by change.routes.collectAsState()
    val changeRoute = changeRoutes.lastOrNull()
    val changeSheet by change.sheet.collectAsState()
    val changeDone by change.done.collectAsState()
    val selected by tabs.selected.collectAsState()
    val appointmentsLoad by appointments.state.collectAsState()
    val awaiting = (appointmentsLoad as? CareLoad.Ready)?.content?.awaiting ?: 0
    val searchRoutes by search.flow.routes.collectAsState()
    val toasts = rememberLumeToastHostState()
    val sentToast = stringResource(Res.string.sent_toast)
    val bookedToast = stringResource(Res.string.booked_toast)
    val onBooked: (AppointmentStatus, Boolean) -> Unit = { status, showAppointments ->
        home.refresh()
        appointments.refresh()
        if (showAppointments) {
            appointments.segmentChosen(AppointmentsSegment.UPCOMING)
            tabs.show(PatientTab.APPOINTMENTS)
            toasts.show(
                if (status == AppointmentStatus.REQUESTED) sentToast else bookedToast,
                LumeStatusStyle.Success,
            )
        }
    }
    val movedToast = stringResource(Res.string.moved_toast)
    val cancelledToast = stringResource(Res.string.cancelled_toast)
    val copiedToast = stringResource(Res.string.directions_copied)
    val rateSent by rate.sent.collectAsState()
    val rateSentToast = stringResource(Res.string.rate_sent_toast)
    LaunchedEffect(rateSent) {
        if (!rateSent) return@LaunchedEffect
        // Inicio stops asking, Ajustes counts one more, and an open c3 shows what was sent.
        home.refresh()
        settings.refresh()
        changeRoutes.lastOrNull()?.let { change.reloadDetail() }
        toasts.show(rateSentToast, LumeStatusStyle.Success)
        rate.sentSeen()
    }
    LaunchedEffect(changeDone) {
        val done = changeDone ?: return@LaunchedEffect
        home.refresh()
        appointments.refresh()
        toasts.show(if (done is ChangeDone.Moved) movedToast else cancelledToast, LumeStatusStyle.Success)
        change.doneSeen()
    }
    // f1: with one map app only, it opens directly and no sheet is drawn (spec f1).
    val openDirections: (PatientAppointment) -> Unit = { appointment ->
        val apps = directions.mapApps()
        if (apps.size == 1) {
            directions.open(apps.single(), "${appointment.place.address} ${appointment.place.detail}")
        } else {
            change.openDirections(appointment)
        }
    }
    val showsTabBar = changeRoute == null && (selected != PatientTab.SEARCH || searchRoutes.last().showsTabBar)

    Box(modifier = Modifier.fillMaxSize()) {
        // The bar floats over the content, which scrolls under it: each screen leaves the kit's clearance at its end.
        // The toasts' state reaches the screens, so a pinned action bar can tell the host where it starts (e10).
        CompositionLocalProvider(
            LocalTabBarClearance provides if (showsTabBar) LumeFloatingTabBarClearance else 0.dp,
            LocalLumeToastHostState provides toasts,
        ) {
            when {
                changeRoute == ChangeRoute.DETAIL -> AppointmentDetailScreen(
                    model = change,
                    today = care.today(),
                    onDirections = openDirections,
                    onCall = contact::dial,
                    onBookAgain = { appointment ->
                        change.launch {
                            doctorToBookAgain(appointment)?.let { doctor ->
                                back()
                                tabs.show(PatientTab.SEARCH)
                                search.launch { schedule(doctor) }
                            }
                        }
                    },
                    onRate = { appointment, stars ->
                        rate.open(RatingPrompt(appointment.id, appointment.doctor, appointment.date), stars)
                    },
                )
                changeRoute == ChangeRoute.RESCHEDULE -> RescheduleScreen(change, toasts, care.today())
                changeRoute == ChangeRoute.CANCEL -> CancelScreen(change, care.today())
                selected == PatientTab.HOME -> HomeScreen(
                    model = home,
                    onDirections = openDirections,
                    onChangeOrCancel = { appointment -> change.launch { openOptions(appointment) } },
                    onOpenAppointment = { appointment -> change.launch { openDetail(appointment) } },
                    onRate = rate::open,
                    onSearch = { tabs.show(PatientTab.SEARCH) },
                    onNotices = {},
                    onProfile = { tabs.show(PatientTab.SETTINGS) },
                )
                selected == PatientTab.SEARCH -> SearchHost(search, onBooked)
                selected == PatientTab.APPOINTMENTS -> AppointmentsScreen(
                    model = appointments,
                    onOpen = { appointment -> change.launch { openDetail(appointment) } },
                    onChange = { appointment -> change.launch { changeTime(appointment) } },
                    onCancel = change::cancelAppointment,
                    onSearch = { tabs.show(PatientTab.SEARCH) },
                )
                selected == PatientTab.DOCTORS -> MyDoctorsScreen(
                    model = doctors,
                    onOpen = {},
                    onCall = contact::dial,
                    onMail = contact::mail,
                    onSearch = { tabs.show(PatientTab.SEARCH) },
                )
                else -> SettingsScreen(
                    model = settings,
                    photo = photo,
                    onEditPhone = {},
                    onEditEmail = {},
                    onEntry = { if (it == SettingsEntry.SIGN_OUT) onSignOut() },
                )
            }
        }
        if (showsTabBar) {
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
                modifier = Modifier.align(Alignment.BottomCenter),
                style = LumeTabBarStyle.Floating,
            )
        }
        DirectionsSheet(
            appointment = (changeSheet as? ChangeSheet.Directions)?.appointment,
            apps = remember(changeSheet) {
                if (changeSheet is ChangeSheet.Directions) directions.mapApps() else emptyList()
            },
            directions = directions,
            clipboard = clipboard,
            onCopied = { toasts.show(copiedToast, LumeStatusStyle.Success) },
            onDismiss = change::dismissSheet,
        )
        OptionsSheet(
            options = (changeSheet as? ChangeSheet.Options)?.options,
            today = care.today(),
            onChange = { appointment -> change.launch { changeTime(appointment) } },
            onCancel = change::cancelAppointment,
            onCall = contact::dial,
            onDismiss = change::dismissSheet,
        )
        RateSheet(rate, onComplaint = {})
        ProfilePhotoFlow(photo, picker, toasts)
        // Above the floating bar, as k3 draws it, never over it.
        LumeToastHost(
            toasts,
            dismissLabel = stringResource(Res.string.toast_dismiss),
            modifier = Modifier.padding(bottom = if (showsTabBar) LumeFloatingTabBarClearance else 0.dp),
            appearance = LumeToastAppearance.Inverted,
        )
    }
}
