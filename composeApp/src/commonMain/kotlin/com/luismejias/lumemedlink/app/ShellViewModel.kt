package com.luismejias.lumemedlink.app

import androidx.lifecycle.ViewModel
import com.luismejias.lumemedlink.core.auth.UnwiredAuthGateway
import com.luismejias.lumemedlink.core.session.InactivityLock
import com.luismejias.lumemedlink.features.auth.flow.AuthFlowSession
import kotlinx.coroutines.flow.MutableStateFlow

/** Inactivity window before re-authentication is demanded (§8.3). */
private const val INACTIVITY_WINDOW_MILLIS = 300_000L

/**
 * The shell's state that must outlive a configuration change (task 0009, ADR-0039).
 *
 * Until 2026-10-07 it lived in `remember` inside `App()`, and Android recreates the Activity — and with
 * it the composition — on a rotation, a theme change or a font-size change. The lock was reborn LOCKED
 * and the session re-probed: fail-closed, so not a hole, but a biometric prompt for turning the phone.
 *
 * What lives here is STATE, never a platform object: the biometric gate holds the Activity it prompts
 * in, and a ViewModel that held it would leak the Activity it outlives. So the window ([inactivity])
 * survives, and the lock built around it is rebuilt by each composition with that composition's gate.
 *
 * Kept in memory only, and that is the line ADR-0039 draws: surviving a rotation is not surviving the
 * process. Nothing here is persisted, so a killed process is still born locked (§8.3).
 */
internal class ShellViewModel : ViewModel() {
    val inactivity = InactivityLock(INACTIVITY_WINDOW_MILLIS)

    /** `null` until the launch probe has answered; a rotation does not ask again. */
    var hasSession: Boolean? = null

    /**
     * The sign-in attempt in progress, if any: it outlives a rotation like the rest of this class, and is
     * replaced by a fresh one whenever the flow restarts or a session begins — LumeMed's wipe-on-leave.
     * The gateway is the unwired one until a backend is deployed (tareas/FREEZE).
     */
    val auth = MutableStateFlow(AuthFlowSession(UnwiredAuthGateway))

    /** "Usar contraseña" on the lock screen: the sign-in flow is shown over a session that still exists. */
    val reauthenticating = MutableStateFlow(false)

    fun freshAuth() {
        auth.value = AuthFlowSession(UnwiredAuthGateway)
    }
}
