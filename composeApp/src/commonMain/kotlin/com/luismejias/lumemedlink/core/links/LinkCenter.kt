package com.luismejias.lumemedlink.core.links

import com.luismejias.lumemedlink.core.session.ElapsedClock
import com.luismejias.lumemedlink.core.telemetry.AnalyticsEvent
import com.luismejias.lumemedlink.core.telemetry.AnalyticsParam
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How long a link waits behind the lock before it is forgotten (ADR-0053 §3). */
internal const val PENDING_LINK_LIFETIME_MILLIS = 5L * 60 * 1000

/** Where an arrival came from; analytics reads it, routing does not. */
internal enum class ArrivalOrigin { LINK, PUSH }

/** A link that reached the app, from a browser, another app or a notification tap. */
internal data class Arrival(val link: DeepLink, val origin: ArrivalOrigin)

/** What the shell knows when a link arrives: who is in, and whether the session is open right now. */
internal data class LinkSession(val role: LinkAudience?, val unlocked: Boolean) {
    companion object {
        val NOBODY = LinkSession(role = null, unlocked = false)
    }
}

/** What becomes of a link. */
internal sealed interface LinkResolution {
    /** Open it now. */
    data class Land(val link: DeepLink) : LinkResolution

    /** Keep it until the session opens — behind the lock, or before sign-in. */
    data object Hold : LinkResolution

    /** It is for the other role: forget it. Re-aiming it somewhere "close" would be guessing. */
    data object Drop : LinkResolution
}

/**
 * The single place that decides (ADR-0053 §4). One exhaustive answer per link, not a chain of routers where the first
 * to claim a path wins: two handlers claiming the same path is the failure this replaces.
 *
 * Every catalogued destination is behind the session (§8.12, ADR-0012): no link opens anything before sign-in or
 * while locked, not even "on top of" the lock. A link grants navigation, never access: the screen it opens still asks
 * the backend, and an id that is not the person's opens nothing.
 */
internal fun resolve(link: DeepLink, session: LinkSession): LinkResolution {
    val role = session.role
    return when {
        role == null || !session.unlocked -> LinkResolution.Hold
        link.entry.audience == LinkAudience.ANY || link.entry.audience == role -> LinkResolution.Land(link)
        else -> LinkResolution.Drop
    }
}

/**
 * Holds at most one link while the session is closed, and hands the hosts what to open (ADR-0053 §3).
 *
 * - One slot: the newest arrival replaces the older, as the person's latest tap is what they meant.
 * - Memory only, so a link never outlives the process, and a link waiting on a shared phone dies with it.
 * - It expires after [PENDING_LINK_LIFETIME_MILLIS] on the elapsed clock, which moving the phone's time cannot stretch.
 * - [forget] at sign-out and logout: one person's link never opens in the next person's session.
 */
internal class LinkCenter(
    private val clock: ElapsedClock,
    private val logEvent: (AnalyticsEvent, Map<AnalyticsParam, Enum<*>>) -> Unit = { _, _ -> },
) {
    private var pending: Arrival? = null
    private var pendingSince = 0L
    private var session = LinkSession.NOBODY

    private val landing = MutableStateFlow<DeepLink?>(null)

    /** What the host in front should open now; it calls [landed] once it has. */
    val toOpen: StateFlow<DeepLink?> = landing.asStateFlow()

    fun arrived(arrival: Arrival) {
        when (val resolution = resolve(arrival.link, session)) {
            is LinkResolution.Land -> open(arrival, resolution.link)
            LinkResolution.Hold -> {
                pending = arrival
                pendingSince = clock.elapsedMillis()
            }
            LinkResolution.Drop -> Unit
        }
    }

    /** The shell's session moved: signed in, unlocked, locked, or a role chosen. A waiting link may now open. */
    fun sessionChanged(now: LinkSession) {
        session = now
        val waiting = pending ?: return
        if (clock.elapsedMillis() - pendingSince > PENDING_LINK_LIFETIME_MILLIS) {
            pending = null
            return
        }
        when (val resolution = resolve(waiting.link, now)) {
            is LinkResolution.Land -> {
                pending = null
                open(waiting, resolution.link)
            }
            LinkResolution.Hold -> Unit
            LinkResolution.Drop -> pending = null
        }
    }

    fun landed() {
        landing.value = null
    }

    /** Sign-out, logout, a change of account: nothing waiting, nothing about to open. */
    fun forget() {
        pending = null
        landing.value = null
        session = LinkSession.NOBODY
    }

    private fun open(arrival: Arrival, link: DeepLink) {
        landing.value = link
        logEvent(
            AnalyticsEvent.DEEP_LINK_OPENED,
            mapOf(AnalyticsParam.DESTINATION to link.entry, AnalyticsParam.ORIGIN to arrival.origin),
        )
    }
}
