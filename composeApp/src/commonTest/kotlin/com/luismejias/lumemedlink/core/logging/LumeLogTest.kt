package com.luismejias.lumemedlink.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LumeLogTest {

    @Test
    fun proseCannotBecomeALogDetail() {
        // The leak this type makes unwritable: a name, a RUT, a server message.
        assertNull(LogDetail.ofEnumName("Perez"))
        assertNull(LogDetail.ofEnumName("11111111-1"))
        assertNull(LogDetail.ofEnumName("control de diabetes"))
        assertNull(LogDetail.ofEnumName("token-abc123"))
    }

    @Test
    fun onlyEnumConstantShapesAreAccepted() {
        assertNotNull(LogDetail.ofEnumName("SESSION_ENDED"))
        assertNotNull(LogDetail.ofEnumName("TOO_MANY_ATTEMPTS"))
    }

    @Test
    fun numbersAndDurationsArePermitted() {
        assertEquals("401", LogDetail.of(401).value)
        assertEquals("250ms", LogDetail.of(250L).value)
    }

    @Test
    fun theDefaultSinkWritesNothing() {
        // Asserting a no-op looks silly and is not: silence is the DECISION (ADR-0020), and if a
        // future edit makes this sink write, this test is what notices.
        DiscardingLogSink.log(LogEvent.SESSION_ENDED, LogDetail.of(1))
    }
}
