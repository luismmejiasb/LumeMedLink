package com.luismejias.lumemedlink.core.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosFramesTest {
    @Test
    fun aKotlinFrameGivesItsClassAndFunction() {
        val frame = frameOf(
            "3   LumeMedLink   0x0000000104a0   kfun:com.luismejias.lumemedlink.app.ShellViewModel#start(){} + 52",
        )
        assertEquals("com.luismejias.lumemedlink.app.ShellViewModel.start", frame)
    }

    @Test
    fun anInternalFunctionReadsTheOtherWayRound() {
        // As the simulator printed it on 2026-10-12, from a forced crash in a coroutine.
        val symbol = "kfun:com.luismejias.lumemedlink.app.CrashTour$1.\$invokeCOROUTINE$0.invokeSuspend#internal"
        assertEquals(
            "com.luismejias.lumemedlink.app.CrashTour$1.\$invokeCOROUTINE$0.invokeSuspend",
            frameOf("2 LumeMedLink 0x0 $symbol + 9"),
        )
    }

    @Test
    fun aNativeFrameIsLeftToCrashlytics() {
        assertNull(frameOf("7   UIKitCore   0x00000001a2b3   -[UIApplication sendEvent:] + 300"))
    }

    @Test
    fun theStackOfARealThrowableParses() {
        // A test binary may be symbolised or not; whatever is found must be well formed.
        IllegalStateException("x").kotlinFrames().forEach { check(it.isNotBlank() && '.' in it) }
    }
}
