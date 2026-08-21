package com.luismejias.lumemedlink.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// All data below is synthetic (§9).

class PushSignalTest {

    @Test
    fun proseCannotBecomeAnOpaqueReference() {
        // The exact leak this type exists to make impossible: a server (or a careless slice)
        // trying to put something a lock-screen reader could understand into the payload.
        assertNull(OpaqueRef.parseOrNull("Control de diabetes"))
        assertNull(OpaqueRef.parseOrNull("Paciente Pérez, oncología"))
        assertNull(OpaqueRef.parseOrNull("cita 10:30 con el psiquiatra"))
        assertNull(OpaqueRef.parseOrNull("11.111.111-1"))
    }

    @Test
    fun oversizedValuesAreRefused() {
        assertNull(OpaqueRef.parseOrNull("a".repeat(65)), "a 65-char field could hold a sentence")
        assertNull(OpaqueRef.parseOrNull("short"), "too short to be a real opaque id")
    }

    @Test
    fun realOpaqueIdentifiersAreAccepted() {
        assertNotNull(OpaqueRef.parseOrNull("a1b2c3d4"))
        assertNotNull(OpaqueRef.parseOrNull("7f3e9c21-4b6a-4d2f-9e18-5c7a0b1d2e3f".replace("-", "_")))
        assertNotNull(OpaqueRef.parseOrNull("appt_01JQ8Z4KM9"))
    }

    @Test
    fun anOpaqueReferenceNeverPrintsItself() {
        val ref = OpaqueRef.parseOrNull("appt_01JQ8Z4KM9")
        assertNotNull(ref)
        assertFalse(ref.toString().contains("appt_01JQ8Z4KM9"), "not even in a log line (§8.1)")
    }

    @Test
    fun everyKindHasFixedAppOwnedCopy() {
        // Pins the property that matters: the visible text is chosen by the APP from the kind, so
        // a server can never decide what a locked screen displays.
        PushSignalKind.entries.forEach { kind ->
            val key = PushSignal(kind).displayCopyKey
            assertTrue(key.startsWith("push."), "copy key must name an app-owned string: $key")
        }
        assertEquals(
            PushSignalKind.entries.size,
            PushSignalKind.entries.map { PushSignal(it).displayCopyKey }.toSet().size,
            "each kind needs its own copy, so none borrows another's meaning",
        )
    }

    @Test
    fun aSignalCarriesNothingReadableEvenWhenPrinted() {
        val signal = PushSignal(
            PushSignalKind.AGENDA_CHANGED,
            OpaqueRef.parseOrNull("appt_01JQ8Z4KM9"),
        )
        val printed = signal.toString()

        assertFalse(printed.contains("appt_01JQ8Z4KM9"))
    }
}
