package com.luismejias.lumemedlink.features.auth.flow

import com.luismejias.lumemedlink.core.auth.AccessFailure
import com.luismejias.lumemedlink.core.auth.AccessResult
import com.luismejias.lumemedlink.resources.Res
import com.luismejias.lumemedlink.resources.common_failed_short
import com.luismejias.lumemedlink.resources.offline_title
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

/**
 * A message for the person, as a KEY — never a resolved string (ADR-0045): a model cannot read resources, and a
 * test compares keys without a locale. The screen resolves it.
 */
internal sealed interface AccessText {
    data class Plain(val key: StringResource) : AccessText

    data class Counted(val key: PluralStringResource, val count: Int) : AccessText
}

/** What a screen's primary action came to. The screen turns it into the button's flash. */
internal sealed interface StepOutcome {
    /** Done; the flow already moved on. */
    data object Advanced : StepOutcome

    /** Refused, and the screen already shows why — the button flashes [text]. */
    data class Rejected(val text: AccessText) : StepOutcome

    /** Something the person did not cause: the flow's alert says the rest. */
    data object Blocked : StepOutcome

    /** Nothing needing an alarm happened — a dismissed prompt — and the person can try again. */
    data class Retry(val text: AccessText) : StepOutcome
}

/** The rejection every screen shares when the phone has no network: its banner is already up. */
internal val OfflineOutcome = StepOutcome.Rejected(AccessText.Plain(Res.string.offline_title))

/** A rejection the screen explains with its own inline state. */
internal val ShortRejection = StepOutcome.Rejected(AccessText.Plain(Res.string.common_failed_short))

/**
 * The access flow's coordinator (§5, ADR-0045: navigation decided by a coordinator, rendered by a host). It owns
 * the path, the direction of the last move, whether the server alert is up, and how the flow ended. Memory only.
 *
 * Pure Kotlin with no Compose and no platform types, so every transition is pinned by a test.
 */
internal class AccessFlowModel(
    /** Told when somebody came in — for the catalog's sign-in event (ADR-0052 §3), never with who. */
    private val onFinished: (AccessRole) -> Unit = {},
) {
    private val path = MutableStateFlow(listOf(AccessRoute.WELCOME))
    private val move = MutableStateFlow(AccessMove.FORWARD)
    private val serverAlert = MutableStateFlow(false)
    private val retries = MutableStateFlow(0)
    private val ending = MutableStateFlow<AccessRole?>(null)

    /** Which app the account opens, as sign-in answered it (ADR-0056); the flow finishes with it. */
    var signedInAs: AccessRole = AccessRole.PATIENT

    val routes: StateFlow<List<AccessRoute>> = path.asStateFlow()
    val lastMove: StateFlow<AccessMove> = move.asStateFlow()

    /** `true` while "Estamos con problemas" is shown — a server failure, never a typing one. */
    val unavailable: StateFlow<Boolean> = serverAlert.asStateFlow()

    /** Bumped by the alert's retry; the screen on top re-runs its primary action. */
    val retryEpoch: StateFlow<Int> = retries.asStateFlow()

    /** Set when somebody came in. The shell shows that role's (pending) main app. */
    val finished: StateFlow<AccessRole?> = ending.asStateFlow()

    val top: AccessRoute get() = path.value.last()

    fun push(route: AccessRoute) {
        move.value = AccessMove.FORWARD
        path.value = path.value + route
    }

    /** Back, only where the route allows it. */
    fun back(): Boolean {
        if (path.value.size < 2 || !top.allowsBack) return false
        move.value = AccessMove.BACKWARD
        path.value = path.value.dropLast(1)
        return true
    }

    /** Back to the last [route] in the path, dropping what is above it; `false` if it is not there. */
    fun backTo(route: AccessRoute): Boolean {
        val index = path.value.lastIndexOf(route)
        if (index < 0) return false
        move.value = AccessMove.BACKWARD
        path.value = path.value.take(index + 1)
        return true
    }

    /** Replaces the whole path — an outcome that must not be backed out of, like "Ir a ingresar". */
    fun replaceWith(vararg routes: AccessRoute) {
        move.value = AccessMove.FORWARD
        path.value = routes.toList()
    }

    fun finish(role: AccessRole) {
        ending.value = role
        onFinished(role)
    }

    /** The server could not answer: the alert, and the step can be retried from it. */
    fun blocked(): StepOutcome {
        serverAlert.value = true
        return StepOutcome.Blocked
    }

    fun alertRetried() {
        serverAlert.value = false
        retries.value += 1
    }

    fun alertDismissed() {
        serverAlert.value = false
    }
}

/**
 * Settles a gateway answer the way every screen does: no network → the screen's banner ([onOffline]); a server
 * that answered nothing usable → the flow's alert; anything else → the screen decides, and a failure it does not
 * name is the server's too.
 */
internal inline fun <T> AccessResult<T>.settle(
    flow: AccessFlowModel,
    onOffline: () -> Unit,
    onOk: (T) -> StepOutcome,
    onFailure: (AccessFailure) -> StepOutcome? = { null },
): StepOutcome = when (this) {
    is AccessResult.Ok -> onOk(value)
    is AccessResult.Failed -> when (failure) {
        AccessFailure.Offline -> {
            onOffline()
            OfflineOutcome
        }
        AccessFailure.Unavailable -> flow.blocked()
        else -> onFailure(failure) ?: flow.blocked()
    }
}
