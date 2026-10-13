package com.luismejias.lumemedlink.core.telemetry

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MessageFreeCrashTest {
    private val leaky = "GET /v1/patients?rut=11.111.111-1 failed for Ana Pérez"

    @Test
    fun theCopyKeepsTheTypeAndTheFramesAndNoMessage() {
        val original = IllegalStateException(leaky)
        val copy = original.withoutMessages()

        assertEquals("java.lang.IllegalStateException", copy.message)
        assertContentEquals(original.stackTrace, copy.stackTrace)
        assertFalse(copy.toString().contains("rut"))
    }

    @Test
    fun noCauseCarriesItsMessageEither() {
        val copy = RuntimeException("outer $leaky", IllegalArgumentException(leaky)).withoutMessages()

        val chain = generateSequence(copy) { it.cause }.toList()
        assertEquals(2, chain.size)
        assertEquals("java.lang.IllegalArgumentException", chain[1].message)
        assertTrue(chain.none { it.message.orEmpty().contains("Ana") })
    }

    @Test
    fun aCauseThatPointsAtItselfEndsTheChain() {
        val looping = object : RuntimeException(leaky) {
            override val cause: Throwable get() = this
        }
        assertEquals(null, looping.withoutMessages().cause)
    }

    @Test
    fun theHandlerHandsOnTheCopyNeverTheOriginal() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val received = mutableListOf<Throwable>()
        try {
            Thread.setDefaultUncaughtExceptionHandler { _, thrown -> received += thrown }
            installMessageFreeCrashes()
            installMessageFreeCrashes()

            val original = IllegalStateException(leaky)
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), original)

            assertEquals(1, received.size, "installing twice must not hand the crash on twice")
            assertFalse(received.single() === original)
            assertIs<MessageFreeCrash>(received.single())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }
}
