package com.luismejias.lumemedlink.features.auth.flow

import com.luismejias.lumemedlink.core.auth.AuthFailure
import com.luismejias.lumemedlink.core.session.SessionTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The one alert the flow can show at a time (LumeMed: a single error surface for the whole flow). */
internal data class AuthAlert(
    val title: String,
    val message: String,
    /** `true`: the confirm action retries the step that failed; `false`: it only closes. */
    val retryable: Boolean,
    /** Where closing the alert leaves the person: on the same screen, or back at the start. */
    val restartsFlow: Boolean = false,
)

/** What a screen's primary action came to. The screen turns it into the button's flash. */
internal sealed interface StepOutcome {
    /** Done; the flow already moved on. */
    data object Advanced : StepOutcome

    /** The server refused what was typed: answered on the button, nothing else. */
    data class Rejected(val message: String) : StepOutcome

    /** Something the person did not cause: answered by the flow's alert. */
    data object Blocked : StepOutcome

    /** Nothing happened that needs an alarm — a dismissed prompt — and the person can try again. */
    data class Retry(val message: String) : StepOutcome
}

/**
 * The sign-in flow's coordinator (§5: navigation decided by a coordinator, rendered by the host — LumeMed's
 * ADR-0014). It owns the path, the direction of the last move, the single alert, and what one step hands
 * the next: the authenticator material, the session's tokens. Memory only — nothing here is saved, so a
 * killed process starts the flow over (§8.3), and leaving the flow drops all of it.
 *
 * Pure Kotlin with no Compose and no platform types, so every transition is pinned by a test.
 */
internal class AuthFlowModel {
    private val path = MutableStateFlow(listOf(AuthRoute.LOGIN))
    private val move = MutableStateFlow(AuthMove.FORWARD)
    private val currentAlert = MutableStateFlow<AuthAlert?>(null)
    private val retries = MutableStateFlow(0)
    private val restartCount = MutableStateFlow(0)

    val routes: StateFlow<List<AuthRoute>> = path.asStateFlow()
    val lastMove: StateFlow<AuthMove> = move.asStateFlow()
    val alert: StateFlow<AuthAlert?> = currentAlert.asStateFlow()

    /** Bumped by an alert's retry; the screen on top re-runs its primary action (the kit's run trigger). */
    val retryEpoch: StateFlow<Int> = retries.asStateFlow()

    /** Bumped on every restart, so the owner can replace this attempt with an empty one. */
    val restarts: StateFlow<Int> = restartCount.asStateFlow()

    /** The session's tokens, between the second factor and the biometric enrollment. Never persisted. */
    var pendingTokens: SessionTokens? = null
        private set

    val top: AuthRoute get() = path.value.last()

    fun push(route: AuthRoute) {
        move.value = AuthMove.FORWARD
        path.value = path.value + route
    }

    /** Back, only where the route allows it (spent steps do not go back). */
    fun back(): Boolean {
        if (path.value.size < 2 || !top.allowsBack) return false
        move.value = AuthMove.BACKWARD
        path.value = path.value.dropLast(1)
        return true
    }

    /** Back to the start, dropping everything the flow was holding. */
    fun restart() {
        pendingTokens = null
        currentAlert.value = null
        move.value = AuthMove.BACKWARD
        path.value = listOf(AuthRoute.LOGIN)
        restartCount.value += 1
    }

    /** The second factor passed: the tokens wait here while the biometric material is made. */
    fun tokensReceived(tokens: SessionTokens) {
        pendingTokens = tokens
        push(AuthRoute.BIOMETRIC_ENROLLMENT)
    }

    /** Takes the tokens out, once: the session entry owns them from here. */
    fun takeTokens(): SessionTokens? = pendingTokens.also { pendingTokens = null }

    /** Turns a failure the person did not cause into the flow's alert. Returns the step's outcome. */
    fun blockedBy(failure: AuthFailure): StepOutcome {
        currentAlert.value = when (failure) {
            AuthFailure.RateLimited -> AuthAlert(
                AuthCopy.RATE_LIMITED_TITLE,
                AuthCopy.RATE_LIMITED_MESSAGE,
                retryable = false,
            )
            else -> AuthAlert(AuthCopy.UNAVAILABLE_TITLE, AuthCopy.UNAVAILABLE_MESSAGE, retryable = true)
        }
        return StepOutcome.Blocked
    }

    /** An alert that ends the attempt: closing it goes back to the start. */
    fun endWith(title: String, message: String): StepOutcome {
        currentAlert.value = AuthAlert(title, message, retryable = false, restartsFlow = true)
        return StepOutcome.Blocked
    }

    /** The alert's confirm action. */
    fun alertConfirmed() {
        val shown = currentAlert.value ?: return
        currentAlert.value = null
        when {
            shown.restartsFlow -> restart()
            shown.retryable -> retries.value += 1
        }
    }

    fun alertDismissed() {
        val shown = currentAlert.value ?: return
        currentAlert.value = null
        if (shown.restartsFlow) restart()
    }
}
