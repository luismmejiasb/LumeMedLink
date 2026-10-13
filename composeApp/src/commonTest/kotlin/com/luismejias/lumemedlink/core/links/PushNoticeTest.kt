package com.luismejias.lumemedlink.core.links

import com.luismejias.lumemedlink.shared.PushSignalKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// All data below is synthetic (§9).
class PushNoticeTest {
    @Test
    fun aReminderOpensTheAppointmentItNames() {
        val notice = pushNoticeOf(mapOf("kind" to "APPOINTMENT_REMINDER", "link" to "citas/detalle?cita=a-rendon-0902"))
        assertEquals(PushSignalKind.APPOINTMENT_REMINDER, notice?.signal?.kind)
        assertEquals(DeepLinkEntry.APPOINTMENT_DETAIL, notice?.link?.entry)
    }

    @Test
    fun aLinkOutsideTheKindFallsBackToTheKindsOwnPlace() {
        // The lock screen says «recordatorio de cita»: the tap cannot open the analytics consent instead.
        val notice = pushNoticeOf(mapOf("kind" to "APPOINTMENT_REMINDER", "link" to "configuracion/privacidad"))
        assertEquals(DeepLink(DeepLinkEntry.APPOINTMENTS), notice?.link)
    }

    @Test
    fun aPayloadCannotPointOutsideTheApp() {
        val absolute = pushNoticeOf(mapOf("kind" to "OFFICIAL_NOTICE", "link" to "https://evil.test/avisos"))
        assertEquals(DeepLink(DeepLinkEntry.NOTICES), absolute?.link)
        val scheme = pushNoticeOf(mapOf("kind" to "OFFICIAL_NOTICE", "link" to "//evil.test/avisos"))
        assertEquals(DeepLink(DeepLinkEntry.NOTICES), scheme?.link)
    }

    @Test
    fun anUnknownKindIsNoNotice() {
        assertNull(pushNoticeOf(mapOf("kind" to "Tu control de diabetes es mañana")))
        assertNull(pushNoticeOf(mapOf("link" to "citas")))
    }

    @Test
    fun aRevokedSessionOpensNothing() {
        assertNull(pushNoticeOf(mapOf("kind" to "SESSION_REVOKED", "link" to "citas"))?.link)
    }

    @Test
    fun textInThePayloadReachesNothing() {
        val notice = pushNoticeOf(
            mapOf("kind" to "OFFICIAL_NOTICE", "title" to "Resultados de su biopsia", "body" to "Paciente Pérez"),
        )
        assertEquals("push.official_notice", notice?.signal?.displayCopyKey)
        assertEquals(DeepLink(DeepLinkEntry.NOTICES), notice?.link)
    }

    @Test
    fun theDoctorsAgendaSignalStaysOnTheDoctorsSide() {
        val notice = pushNoticeOf(mapOf("kind" to "AGENDA_CHANGED", "link" to "citas"))
        assertEquals(DeepLink(DeepLinkEntry.DOCTOR_AGENDA), notice?.link)
    }
}
