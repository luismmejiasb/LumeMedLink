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
import cl.lume.uicomposer.components.LumeNavigationStack
import cl.lume.uicomposer.components.LumeTabBar
import cl.lume.uicomposer.components.LumeTabBarItem
import cl.lume.uicomposer.components.LumeTabBarStyle
import cl.lume.uicomposer.components.LumeToastAppearance
import cl.lume.uicomposer.components.LumeToastHost
import cl.lume.uicomposer.components.rememberLumeToastHostState
import cl.lume.uicomposer.foundations.LumeIcon
import cl.lume.uicomposer.tokens.LumeStatusStyle
import com.luismejias.lumemedlink.core.appearance.Appearance
import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.ContactKind
import com.luismejias.lumemedlink.core.care.DirectoryGateway
import com.luismejias.lumemedlink.core.care.InMemoryProfilePhoto
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.PatientCareGateway
import com.luismejias.lumemedlink.core.care.PatientProfile
import com.luismejias.lumemedlink.core.care.RatingPrompt
import com.luismejias.lumemedlink.core.device.platformPhotoCodec
import com.luismejias.lumemedlink.core.device.rememberAddressClipboard
import com.luismejias.lumemedlink.core.device.rememberDeviceContact
import com.luismejias.lumemedlink.core.device.rememberDirections
import com.luismejias.lumemedlink.core.device.rememberPhotoPicker
import com.luismejias.lumemedlink.core.features.Feature
import com.luismejias.lumemedlink.core.features.FeatureGate
import com.luismejias.lumemedlink.core.links.DeepLink
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.core.screen.LocalStandInsShown
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
import com.luismejias.lumemedlink.features.patient.change.changeSourcesOf
import com.luismejias.lumemedlink.features.patient.doctors.MyDoctorsScreen
import com.luismejias.lumemedlink.features.patient.doctors.MyDoctorsViewModel
import com.luismejias.lumemedlink.features.patient.home.HomeScreen
import com.luismejias.lumemedlink.features.patient.home.HomeViewModel
import com.luismejias.lumemedlink.features.patient.home.homeSources
import com.luismejias.lumemedlink.features.patient.notices.NoticesScreen
import com.luismejias.lumemedlink.features.patient.notices.NoticesViewModel
import com.luismejias.lumemedlink.features.patient.rating.RateSheet
import com.luismejias.lumemedlink.features.patient.rating.RateViewModel
import com.luismejias.lumemedlink.features.patient.search.SearchHost
import com.luismejias.lumemedlink.features.patient.search.SearchViewModel
import com.luismejias.lumemedlink.features.patient.search.searchSourcesOf
import com.luismejias.lumemedlink.features.patient.settings.ContactChangeSheet
import com.luismejias.lumemedlink.features.patient.settings.ContactChangeViewModel
import com.luismejias.lumemedlink.features.patient.settings.SettingsEntry
import com.luismejias.lumemedlink.features.patient.settings.SettingsPage
import com.luismejias.lumemedlink.features.patient.settings.SettingsRoute
import com.luismejias.lumemedlink.features.patient.settings.SettingsScreen
import com.luismejias.lumemedlink.features.patient.settings.SettingsViewModel
import com.luismejias.lumemedlink.features.patient.settings.settingsSources
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.booked_toast
import com.luismejias.lumemedlink.resources.cancelled_toast
import com.luismejias.lumemedlink.resources.contact_email_done
import com.luismejias.lumemedlink.resources.contact_phone_done
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

/** The feature a tab shows (ADR-0060); `null` for the shell's tabs, which are always there. */
internal fun PatientTab.feature(): Feature? = when (this) {
    PatientTab.HOME, PatientTab.SETTINGS -> null
    PatientTab.SEARCH -> Feature.DOCTOR_SEARCH
    PatientTab.APPOINTMENTS -> Feature.APPOINTMENTS
    PatientTab.DOCTORS -> Feature.MY_DOCTORS
}

/** The tabs [available] lets the patient see, in the design's order. */
internal fun visiblePatientTabs(available: Set<Feature>): List<PatientTab> =
    PatientTab.entries.filter { tab -> tab.feature()?.let { it in available } ?: true }

/** [selected] while it is visible; «Inicio» once its feature is switched off under the person. */
internal fun shownPatientTab(selected: PatientTab, visible: List<PatientTab>): PatientTab =
    if (selected in visible) selected else PatientTab.HOME

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

/** What the patient's stack holds: a tab's root, and what it pushed. */
private sealed interface PatientScreen {
    data class Tab(val tab: PatientTab) : PatientScreen

    data class Change(val route: ChangeRoute) : PatientScreen

    data class Settings(val route: SettingsRoute) : PatientScreen

    /** «Notificaciones», over «Inicio». */
    data object Notices : PatientScreen
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
    appearance: Appearance,
    logEvent: (AnalyticsEvent) -> Unit,
    /** What the patient may see (ADR-0060). */
    features: FeatureGate,
    onSignOut: () -> Unit,
    /**
     * Whether a waiting screen may be drawn from the design's script (ADR-0055). Off against the platform: the kit's
     * placeholder was measured drawing that script unredacted, so the wait read as the person's own data.
     */
    showsStandIns: Boolean = true,
    /** What a link or a notification asks to open now (ADR-0053); [onLinkOpened] once it has. */
    link: DeepLink? = null,
    onLinkOpened: () -> Unit = {},
) {
    CompositionLocalProvider(LocalStandInsShown provides showsStandIns) {
        PatientTabs(care, directory, consent, appearance, logEvent, features, onSignOut, link, onLinkOpened)
    }
}

@Composable
@Suppress("LongParameterList")
private fun PatientTabs(
    care: PatientCareGateway,
    directory: DirectoryGateway,
    consent: TelemetryConsent,
    appearance: Appearance,
    logEvent: (AnalyticsEvent) -> Unit,
    /** What the patient may see (ADR-0060). */
    features: FeatureGate,
    onSignOut: () -> Unit,
    /** What a link or a notification asks to open now (ADR-0053); [onLinkOpened] once it has. */
    link: DeepLink?,
    onLinkOpened: () -> Unit,
) {
    val tabs = viewModel { PatientTabsViewModel() }
    val home = viewModel { HomeViewModel(care.homeSources()) }
    val search = viewModel { SearchViewModel(searchSourcesOf(care, directory), logEvent) }
    val appointments = viewModel { AppointmentsViewModel(care, care) }
    val doctors = viewModel { MyDoctorsViewModel(care) }
    val settings = viewModel {
        SettingsViewModel(care.settingsSources(), consent, appearance) { Res.readBytes(LICENSES_FILE).decodeToString() }
    }
    val settingsRoutes by settings.routes.collectAsState()
    val contactChange = viewModel { ContactChangeViewModel(care) }
    val contactDone by contactChange.done.collectAsState()
    val change = viewModel { ChangeViewModel(changeSourcesOf(care, directory), logEvent) }
    val rate = viewModel { RateViewModel(care, logEvent) }
    val notices = viewModel { NoticesViewModel(care) }
    val noticesOpen by notices.open.collectAsState()
    val noticesSeen by notices.seen.collectAsState()
    // The patient's photo on the platform (ADR-0064). The contract has no photo operations yet (backend task `0150`,
    // PENDING): until it does, the platform's side is held in memory, inside the session's store.
    val photo = viewModel { ProfilePhotoModel(InMemoryProfilePhoto(), platformPhotoCodec()) }
    val picker = rememberPhotoPicker(photo::picked)
    val contact = rememberDeviceContact()
    val directions = rememberDirections()
    val clipboard = rememberAddressClipboard()
    val changeRoutes by change.routes.collectAsState()
    val changeRoute = changeRoutes.lastOrNull()
    val changeSheet by change.sheet.collectAsState()
    val changeDone by change.done.collectAsState()
    val available by features.available.collectAsState()
    val visibleTabs = visiblePatientTabs(available)
    val can = patientActions(available)
    val chosen by tabs.selected.collectAsState()
    val selected = shownPatientTab(chosen, visibleTabs)
    val settingsRoute = settingsRoutes.lastOrNull().takeIf { selected == PatientTab.SETTINGS }
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
    LaunchedEffect(link) {
        val opening = link ?: return@LaunchedEffect
        openPatientLink(opening, tabs, change, rate, notices)
        onLinkOpened()
    }
    LaunchedEffect(noticesSeen) {
        if (!noticesSeen) return@LaunchedEffect
        // The bell on «Inicio» stops calling them new.
        home.refresh()
        notices.seenHandled()
    }
    LaunchedEffect(rateSent) {
        if (!rateSent) return@LaunchedEffect
        // Inicio stops asking, Ajustes counts one more, and an open c3 shows what was sent.
        home.refresh()
        settings.refresh()
        changeRoutes.lastOrNull()?.let { change.reloadDetail() }
        toasts.show(rateSentToast, LumeStatusStyle.Success)
        rate.sentSeen()
    }
    val phoneDoneToast = stringResource(Res.string.contact_phone_done)
    val emailDoneToast = stringResource(Res.string.contact_email_done)
    LaunchedEffect(contactDone) {
        val kind = contactDone ?: return@LaunchedEffect
        // Configuración reads the new contact, and Inicio's greeting stays as it was: neither draws the contact twice.
        settings.refresh()
        toasts.show(if (kind == ContactKind.PHONE) phoneDoneToast else emailDoneToast, LumeStatusStyle.Success)
        contactChange.doneSeen()
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
    // A link can open the list too, so its feature is checked here as well as on the bell (ADR-0060).
    val showsNotices = noticesOpen && can.notices && selected == PatientTab.HOME
    val patientStack = buildList {
        add(PatientScreen.Tab(selected))
        if (showsNotices) add(PatientScreen.Notices)
        settingsRoute?.let { settingsRoutes.forEach { add(PatientScreen.Settings(it)) } }
        changeRoutes.forEach { add(PatientScreen.Change(it)) }
    }
    val showsTabBar =
        changeRoute == null &&
            settingsRoute == null &&
            !showsNotices &&
            (selected != PatientTab.SEARCH || searchRoutes.last().showsTabBar)

    Box(modifier = Modifier.fillMaxSize()) {
        // The bar floats over the content, which scrolls under it: each screen leaves the kit's clearance at its end.
        // The toasts' state reaches the screens, so a pinned action bar can tell the host where it starts (e10).
        CompositionLocalProvider(
            LocalTabBarClearance provides if (showsTabBar) LumeFloatingTabBarClearance else 0.dp,
            LocalLumeToastHostState provides toasts,
        ) {
            // One stack for the patient (kit 227a717): the tab's root, then what it pushed — an appointment's
            // detail and its change, or Configuración's pages. A tab switch replaces the root without a movement.
            LumeNavigationStack(
                stack = patientStack,
                onBack = {
                    when {
                        changeRoutes.isNotEmpty() -> change.back()
                        noticesOpen -> notices.close()
                        else -> settings.back()
                    }
                },
            ) { screen ->
                when (screen) {
                    is PatientScreen.Change -> when (screen.route) {
                        ChangeRoute.DETAIL -> AppointmentDetailScreen(
                            model = change,
                            today = care.today(),
                            onDirections = openDirections,
                            onCall = contact::dial,
                            canChange = can.changeAppointments,
                            onBookAgain = { appointment: PatientAppointment ->
                                change.launch {
                                    doctorToBookAgain(appointment)?.let { doctor ->
                                        back()
                                        tabs.show(PatientTab.SEARCH)
                                        search.launch { schedule(doctor) }
                                    }
                                }
                            }.onlyIf(can.book),
                            onRate = { appointment: PatientAppointment, stars: Int ->
                                rate.open(RatingPrompt(appointment.id, appointment.doctor, appointment.date), stars)
                            }.onlyIf(can.rate),
                        )
                        ChangeRoute.RESCHEDULE -> RescheduleScreen(change, toasts, care.today())
                        ChangeRoute.CANCEL -> CancelScreen(change, care.today())
                    }
                    is PatientScreen.Settings -> SettingsPage(screen.route, settings)
                    PatientScreen.Notices -> NoticesScreen(notices)
                    is PatientScreen.Tab -> when (screen.tab) {
                        PatientTab.HOME -> HomeScreen(
                            model = home,
                            onDirections = openDirections,
                            onChangeOrCancel = { appointment: PatientAppointment ->
                                change.launch { openOptions(appointment) }
                            }.onlyIf(can.changeAppointments),
                            onOpenAppointment = { appointment -> change.launch { openDetail(appointment) } },
                            onRate = rate::open.onlyIf(can.rate),
                            onSearch = { tabs.show(PatientTab.SEARCH) }.onlyIf(can.search),
                            onNotices = notices::show.onlyIf(can.notices),
                            onProfile = { tabs.show(PatientTab.SETTINGS) },
                        )
                        PatientTab.SEARCH -> SearchHost(search, onBooked, canBook = can.book)
                        PatientTab.APPOINTMENTS -> AppointmentsScreen(
                            model = appointments,
                            onOpen = { appointment -> change.launch { openDetail(appointment) } },
                            onChange = { appointment: PatientAppointment ->
                                change.launch { changeTime(appointment) }
                            }.onlyIf(can.changeAppointments),
                            onCancel = change::cancelAppointment.onlyIf(can.changeAppointments),
                            onSearch = { tabs.show(PatientTab.SEARCH) }.onlyIf(can.search),
                        )
                        PatientTab.DOCTORS -> MyDoctorsScreen(
                            model = doctors,
                            onCall = contact::dial,
                            onMail = contact::mail,
                            onSearch = { tabs.show(PatientTab.SEARCH) }.onlyIf(can.search),
                        )
                        PatientTab.SETTINGS -> SettingsScreen(
                            model = settings,
                            photo = photo,
                            onEditPhone = { profile: PatientProfile ->
                                contactChange.open(ContactKind.PHONE, profile)
                            }.onlyIf(can.changeContact),
                            onEditEmail = { profile: PatientProfile ->
                                contactChange.open(ContactKind.EMAIL, profile)
                            }.onlyIf(can.changeContact),
                            showsRatings = can.rate,
                            onEntry = { if (it == SettingsEntry.SIGN_OUT) onSignOut() },
                        )
                    }
                }
            }
        }
        if (showsTabBar) {
            val homeItem = LumeTabBarItem(LumeIcon.Home, stringResource(Res.string.tab_home))
            val searchItem = LumeTabBarItem(LumeIcon.Search, stringResource(Res.string.tab_search))
            val appointmentsItem = LumeTabBarItem(
                LumeIcon.Calendar,
                stringResource(Res.string.tab_appointments),
                badge = LumeCountBadge(
                    awaiting,
                    pluralStringResource(Res.plurals.tab_pending_badge, awaiting, awaiting),
                ),
            )
            val doctorsItem = LumeTabBarItem(LumeIcon.Doctors, stringResource(Res.string.tab_doctors))
            val settingsItem = LumeTabBarItem(LumeIcon.Gear, stringResource(Res.string.tab_settings))
            LumeTabBar(
                items = visibleTabs.map { tab ->
                    when (tab) {
                        PatientTab.HOME -> homeItem
                        PatientTab.SEARCH -> searchItem
                        PatientTab.APPOINTMENTS -> appointmentsItem
                        PatientTab.DOCTORS -> doctorsItem
                        PatientTab.SETTINGS -> settingsItem
                    }
                },
                selected = visibleTabs.indexOf(selected),
                onSelect = { tabs.show(visibleTabs[it]) },
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
        ContactChangeSheet(contactChange)
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

/** «Licencias», generated from the lockfiles (Scripts/generate-licenses.py). */
private const val LICENSES_FILE = "files/licenses.tsv"
