package com.luismejias.lumemedlink.core.links

import com.luismejias.lumemedlink.core.telemetry.AnalyticsEvent
import com.luismejias.lumemedlink.core.telemetry.AnalyticsParam
import com.luismejias.lumemedlink.shared.OpaqueRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// All data below is synthetic (§9).
class LinkCenterTest {
    private var now = 0L
    private val logged = mutableListOf<Pair<AnalyticsEvent, Map<AnalyticsParam, Enum<*>>>>()
    private val center = LinkCenter({ now }) { event, details -> logged += event to details }

    private val patientOpen = LinkSession(LinkAudience.PATIENT, unlocked = true)
    private val detail = DeepLink(DeepLinkEntry.APPOINTMENT_DETAIL, OpaqueRef.parseOrNull("a-rendon-0902"))

    @Test
    fun anOpenSessionOpensAtOnce() {
        center.sessionChanged(patientOpen)
        center.arrived(Arrival(detail, ArrivalOrigin.LINK))
        assertEquals(detail, center.toOpen.value)
    }

    @Test
    fun behindTheLockItWaitsAndOpensOnlyAfterUnlocking() {
        center.sessionChanged(LinkSession(LinkAudience.PATIENT, unlocked = false))
        center.arrived(Arrival(detail, ArrivalOrigin.PUSH))
        assertNull(center.toOpen.value, "no link opens anything while locked, not even on top of the lock")

        center.sessionChanged(patientOpen)
        assertEquals(detail, center.toOpen.value)
    }

    @Test
    fun beforeSignInItWaitsForTheRightRole() {
        center.arrived(Arrival(DeepLink(DeepLinkEntry.DOCTOR_AGENDA), ArrivalOrigin.LINK))
        center.sessionChanged(patientOpen)
        assertNull(center.toOpen.value, "the doctor's link is dropped, not re-aimed at the patient")

        center.sessionChanged(LinkSession(LinkAudience.DOCTOR, unlocked = true))
        assertNull(center.toOpen.value, "and it does not come back for the next person either")
    }

    @Test
    fun aWaitingLinkExpires() {
        center.arrived(Arrival(detail, ArrivalOrigin.PUSH))
        now += PENDING_LINK_LIFETIME_MILLIS + 1
        center.sessionChanged(patientOpen)
        assertNull(center.toOpen.value)
    }

    @Test
    fun theNewestArrivalIsTheOneThatWaits() {
        center.arrived(Arrival(detail, ArrivalOrigin.PUSH))
        center.arrived(Arrival(DeepLink(DeepLinkEntry.SETTINGS), ArrivalOrigin.LINK))
        center.sessionChanged(patientOpen)
        assertEquals(DeepLink(DeepLinkEntry.SETTINGS), center.toOpen.value)
    }

    @Test
    fun signingOutForgetsWhatWasWaiting() {
        center.arrived(Arrival(detail, ArrivalOrigin.PUSH))
        center.forget()
        center.sessionChanged(patientOpen)
        assertNull(center.toOpen.value, "one person's link never opens in the next person's session")
    }

    @Test
    fun aNoticeIsForEitherRole() {
        center.sessionChanged(LinkSession(LinkAudience.DOCTOR, unlocked = true))
        center.arrived(Arrival(DeepLink(DeepLinkEntry.NOTICES), ArrivalOrigin.PUSH))
        assertEquals(DeepLink(DeepLinkEntry.NOTICES), center.toOpen.value)
    }

    @Test
    fun analyticsHearsTheDestinationAndTheOriginNeverTheId() {
        center.sessionChanged(patientOpen)
        center.arrived(Arrival(detail, ArrivalOrigin.PUSH))

        val (event, details) = logged.single()
        assertEquals(AnalyticsEvent.DEEP_LINK_OPENED, event)
        assertEquals(DeepLinkEntry.APPOINTMENT_DETAIL, details[AnalyticsParam.DESTINATION])
        assertEquals(ArrivalOrigin.PUSH, details[AnalyticsParam.ORIGIN])
        assertTrue(details.values.none { it.toString().contains("rendon") })
    }

    @Test
    fun aHeldLinkIsLoggedWhenItOpensNotWhenItArrives() {
        center.arrived(Arrival(detail, ArrivalOrigin.LINK))
        assertTrue(logged.isEmpty())
        center.sessionChanged(patientOpen)
        assertEquals(1, logged.size)
    }
}
