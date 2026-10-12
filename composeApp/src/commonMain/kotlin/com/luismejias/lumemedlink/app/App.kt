package com.luismejias.lumemedlink.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.lume.uicomposer.foundations.LumeBrand
import cl.lume.uicomposer.foundations.LumeTheme
import cl.lume.uicomposer.tokens.LumeCanvas
import cl.lume.uicomposer.tokens.LumeFieldLook
import cl.lume.uicomposer.tokens.LumeNeutrals
import cl.lume.uicomposer.tokens.LumeRadius
import com.luismejias.lumemedlink.core.auth.ScriptedAccessGateway
import com.luismejias.lumemedlink.core.care.ScriptedDoctorCare
import com.luismejias.lumemedlink.core.care.ScriptedPatientCare
import com.luismejias.lumemedlink.core.input.FieldClipboardPolicy
import com.luismejias.lumemedlink.core.logging.DiscardingLogSink
import com.luismejias.lumemedlink.core.logging.LogDetail
import com.luismejias.lumemedlink.core.logging.LogEvent
import com.luismejias.lumemedlink.core.security.NoOpSecurityEventReporter
import com.luismejias.lumemedlink.core.security.emittingIn
import com.luismejias.lumemedlink.core.session.FailedAttemptLedger
import com.luismejias.lumemedlink.core.session.SessionLock
import com.luismejias.lumemedlink.core.session.SessionManager
import com.luismejias.lumemedlink.core.session.SystemClock
import com.luismejias.lumemedlink.core.session.TokenStore
import com.luismejias.lumemedlink.core.session.UnlockGate
import com.luismejias.lumemedlink.core.session.UnlockOutcome
import com.luismejias.lumemedlink.core.session.performLogout
import com.luismejias.lumemedlink.core.session.platformInstallSentinel
import com.luismejias.lumemedlink.core.session.rememberSecureStore
import com.luismejias.lumemedlink.core.session.rememberUnlockGate
import com.luismejias.lumemedlink.features.auth.flow.AccessFlowViewModel
import com.luismejias.lumemedlink.features.auth.flow.AccessHost
import com.luismejias.lumemedlink.features.auth.flow.AccessRole
import com.luismejias.lumemedlink.features.auth.unlock.UnlockScreen
import com.luismejias.lumemedlink.features.doctor.tabs.DoctorMainHost
import com.luismejias.lumemedlink.features.patient.tabs.PatientMainHost
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The design's accent (the handoff's README): the kit derives every other tone from it (ADR-0033, ADR-0044
 * deviation 7). One line, here, so no screen can draw in another brand.
 */
/**
 * The handoff's blue on the house greys: a grey page under white cards, and 14 dp corners on buttons and fields
 * (README «Sistema visual»).
 */
private val LumeMedLinkBrand = LumeBrand(
    seed = Color(0xFF3E63DD),
    neutrals = LumeNeutrals.Slate,
    radius = LumeRadius(medium = 14.dp),
    canvas = LumeCanvas.Grouped,
    // The handoff's fields: the label in the primary ink, figures monospaced, a ✓ when valid, focus and error washed.
    fields = LumeFieldLook.Marked,
)

/**
 * Stand-in when the platform cannot host a biometric gate. It reports [UnlockOutcome.Unavailable],
 * which [SessionLock] turns into an ended session — a missing gate is never an open door.
 */
private object AbsentUnlockGate : UnlockGate {
    override suspend fun enroll(): Boolean = false

    override suspend fun unlock(): UnlockOutcome = UnlockOutcome.Unavailable

    override suspend fun clear() = Unit
}

/**
 * Root composable — the single entry point both platform shells render, and the composition root
 * (ADR-0008): it is the ONE place that wires concrete dependencies together.
 *
 * `public` on purpose: this is the Gradle-module boundary consumed by `:androidApp` and the iOS
 * framework. Everything else in this tree starts `internal`.
 *
 * The whole app renders inside [PrivacyScreenScaffold] (ADR-0010), so no screen can exist without
 * the privacy cover — and inside [LumeTheme] (ADR-0033), once, here, so no screen can draw outside
 * the design system's tokens.
 */
@Composable
public fun App() {
    // What must outlive a rotation lives here; what holds the Activity does not (task 0009, ADR-0039).
    val shell = viewModel { ShellViewModel() }
    // The access flow (ADR-0044) runs against the SCRIPT of a contract that does not exist yet — chosen here and
    // only here (ADR-0045), and it begins no session.
    val access = viewModel {
        val scripted = ScriptedAccessGateway(SystemClock)
        AccessFlowViewModel(patients = scripted, doctors = scripted, clock = SystemClock)
    }
    // The patient's main app (ADR-0046), against the SCRIPT of the design; its ViewModels live in a store of
    // their own that a sign-out empties.
    val patientScope = viewModel { SessionScope() }
    val patientCare = remember { ScriptedPatientCare() }
    val doctorScope = viewModel(key = "doctor-scope") { SessionScope() }
    val doctorCare = remember { ScriptedDoctorCare() }
    val secureStore = rememberSecureStore()
    val unlockGate = rememberUnlockGate(secureStore) ?: AbsentUnlockGate
    val sessionManager = remember(secureStore) {
        SessionManager(TokenStore(secureStore), UnwiredRefreshClient())
    }
    val scope = rememberCoroutineScope()
    // The declared defaults, wired here because this is the composition root (ADR-0008). Both
    // write nothing on purpose — F22 and F23 decided that the default is silence, not that
    // nothing ever calls them.
    val installSentinel = remember { platformInstallSentinel() }
    val logSink = remember { DiscardingLogSink }
    // Still the no-op, and that is now a WIRING gap rather than a missing implementation:
    // `HttpSecurityEventReporter` exists and is tested, but nothing here builds an HttpClient yet
    // (no base URL is configured and no auth flow exists to give it a token). Named so the next
    // reader sees a wire to connect, not a channel to write.
    val securityEvents = remember { NoOpSecurityEventReporter }
    val sessionLock = remember(unlockGate, secureStore, securityEvents, shell) {
        // The attempt count lives in the store, not in this object: `remember` does not survive the
        // process, and a ceiling that resets when the process dies is not a ceiling (ADR-0034). The
        // lock reports what it decides itself (task 0018); the launch is this composition's.
        SessionLock(
            shell.inactivity,
            unlockGate,
            FailedAttemptLedger(secureStore),
            securityEvents.emittingIn(scope),
        )
    }

    var hasSession by remember { mutableStateOf(shell.hasSession ?: false) }
    val reauthenticating by shell.reauthenticating.collectAsState()
    val accessAttempt by access.attempt.collectAsState()
    val accessFinished by accessAttempt.flow.finished.collectAsState()
    var locked by remember { mutableStateOf(sessionLock.isLocked()) }
    var returns by remember { mutableStateOf(0) }

    // COMING BACK RE-ASKS THE LOCK (2026-10-07, task 0002 / F01). The timer below runs on the
    // dispatcher's clock, and that clock stops while the device sleeps — the very clocks ADR-0032
    // rejected for MEASURING the window. The measurement was fixed; the trigger was not: a phone that
    // slept for an hour came back with the agenda on screen until the leftover awake time ran out or
    // somebody touched it. So the lock is re-read on ON_START, while the privacy cover is still up
    // (it only drops at RESUMED), and the timer restarts from what is really left. Reading can only
    // CLOSE the lock: InactivityLock opens on a real re-authentication and nothing else.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, sessionLock) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                locked = sessionLock.isLocked()
                returns += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // THE WINDOW CLOSES ON ITS OWN. Until 2026-09-21 nothing re-read the lock except the pointer
    // handler below, so five minutes could elapse with the agenda on screen and the app would only
    // notice when somebody touched it — the one moment the person is already looking at it. The
    // threat this app ranks FIRST is the phone left on a table (§8.17), and that is exactly the
    // case with no touches in it.
    //
    // It sleeps for the remaining time rather than polling, and re-asks after waking: if activity
    // slid the window while this was suspended, `millisUntilLock` simply returns a new positive
    // number and it sleeps again. No restart needed, no tick to tune.
    LaunchedEffect(sessionLock, hasSession, locked, returns) {
        if (!hasSession || locked) return@LaunchedEffect
        while (true) {
            val remaining = sessionLock.millisUntilLock()
            if (remaining <= 0L) {
                locked = true
                return@LaunchedEffect
            }
            delay(remaining)
        }
    }

    LaunchedEffect(sessionManager) {
        // The store is a CAPABILITY and a capability can be unavailable at launch. The decision
        // about what to do then lives in probeSession, outside this composable, so a test can
        // assert it (ADR-0025).
        // Once per process, not once per composition: a rotation recreates this composable and must
        // not re-run the launch probe (task 0009). The ViewModel remembers the answer, in memory only.
        hasSession = shell.hasSession
            ?: probeSession(sessionManager, installSentinel, secureStore, logSink, securityEvents)
        shell.hasSession = hasSession
    }

    // THE SERVER ENDED IT (task 0020). A refused refresh erases only the token entry — not a logout
    // (ADR-0036 point 1) — so the shell returns to Login WITHOUT the wipe; whatever the dead session
    // left is erased by establishSession before the next one begins (ADR-0037).
    LaunchedEffect(sessionManager) {
        sessionManager.sessionEnded.collect { ended ->
            if (ended) {
                hasSession = false
                shell.hasSession = false
                locked = true
            }
        }
    }

    suspend fun endSession() {
        // The contract itself lives in core/session so a test can reach every branch of it; what
        // stays here is the shell's reaction (ADR-0014, amended 2026-09-21). The four steps used to
        // be this inline sequence, which meant the first throw skipped the rest and the shell's
        // dying scope could truncate the erase.
        val outcome = performLogout(sessionManager, secureStore, unlockGate, sessionLock)
        if (!outcome.complete) {
            // Ends the session anyway — fail closed — but the shell now KNOWS the erase was partial.
            // Telling the person is the login slice's job: this screen is a placeholder and a
            // reassuring "sesión cerrada" over a surviving token is exactly the §8.7 vocabulary
            // failure, with the stakes reversed.
            logSink.log(LogEvent.LOGOUT_INCOMPLETE, LogDetail.ofEnumName(outcome.failed.first().name))
        }
        hasSession = false
        shell.hasSession = false
        shell.reauthenticating.value = false
        access.restart()
        patientScope.reset()
        doctorScope.reset()
        locked = true
    }

    LumeTheme(brand = LumeMedLinkBrand) {
        FieldClipboardPolicy {
            PrivacyScreenScaffold {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Observes pointer activity on the INITIAL pass without consuming it, so the
                        // inactivity window slides while the doctor is actually using the app. Recording
                        // activity can never unlock (InactivityLock enforces that) — it only postpones.
                        .pointerInput(sessionLock) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial)
                                    sessionLock.recordActivity()
                                    locked = sessionLock.isLocked()
                                }
                            }
                        },
                ) {
                    // No session can begin from the access flow yet (ADR-0044): a patient lands in the scripted
                    // main app (ADR-0046), a doctor in the pending one.
                    val accessFlow = @Composable { onCancel: (() -> Unit)? ->
                        val role = accessFinished
                        when (role) {
                            AccessRole.PATIENT -> InSessionScope(patientScope) {
                                PatientMainHost(
                                    care = patientCare,
                                    directory = patientCare,
                                    onSignOut = {
                                        patientScope.reset()
                                        access.restart()
                                    },
                                )
                            }
                            AccessRole.DOCTOR -> InSessionScope(doctorScope) {
                                DoctorMainHost(
                                    care = doctorCare,
                                    onSignOut = {
                                        doctorScope.reset()
                                        access.restart()
                                    },
                                )
                            }
                            null -> AccessHost(viewModel = access, onCancel = onCancel)
                        }
                    }
                    when (resolveDestination(hasSession, locked)) {
                        AppDestination.Login -> accessFlow(null)

                        AppDestination.Locked -> if (reauthenticating) {
                            accessFlow {
                                shell.reauthenticating.value = false
                                access.restart()
                            }
                        } else {
                            UnlockScreen(
                                retryEpoch = 0,
                                onUnlock = { sessionLock.attemptUnlock() },
                                onUnlocked = { locked = false },
                                // Too many misses, a changed enrollment, or no biometrics at all: the
                                // session ends and the doctor signs in again. Fail closed.
                                onSessionEnded = { scope.launch { endSession() } },
                                // A new sign-in replaces this session; establishSession erases it first.
                                onUsePassword = {
                                    access.restart()
                                    shell.reauthenticating.value = true
                                },
                                onChangeAccount = { scope.launch { endSession() } },
                            )
                        }

                        AppDestination.Home -> HomeScreen(
                            onSignOut = { scope.launch { endSession() } },
                        )
                    }
                }
            }
        }
    }
}
