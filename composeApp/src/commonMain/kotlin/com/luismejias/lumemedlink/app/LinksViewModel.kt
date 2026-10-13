package com.luismejias.lumemedlink.app

import androidx.lifecycle.ViewModel
import com.luismejias.lumemedlink.core.links.LinkCenter
import com.luismejias.lumemedlink.core.session.SystemElapsedClock
import com.luismejias.lumemedlink.core.telemetry.AnalyticsEvent
import com.luismejias.lumemedlink.core.telemetry.AnalyticsParam

/**
 * Keeps the link centre across a rotation (ADR-0039's reason, ADR-0053): a link waiting behind the lock must not be
 * dropped because the phone turned. Memory only — it still dies with the process, on purpose.
 */
internal class LinksViewModel : ViewModel() {
    private var center: LinkCenter? = null

    /** The centre, made once with the first [logEvent] it is handed; the telemetry outlives no process either. */
    fun center(logEvent: (AnalyticsEvent, Map<AnalyticsParam, Enum<*>>) -> Unit): LinkCenter =
        center ?: LinkCenter(SystemElapsedClock, logEvent).also { center = it }
}
