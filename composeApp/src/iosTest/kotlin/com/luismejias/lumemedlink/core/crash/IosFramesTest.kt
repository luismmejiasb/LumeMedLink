package com.luismejias.lumemedlink.core.crash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosFramesTest {
    @Test
    fun aKotlinFrameGivesItsClassAndFunction() {
        val frame =
            framesOf(
                "3   LumeMedLink   0x0000000104a0   kfun:com.luismejias.lumemedlink.app.ShellViewModel#start(){} + 52",
            )
        assertEquals(CrashFrame("com.luismejias.lumemedlink.app.ShellViewModel", "start"), frame)
    }

    @Test
    fun anInternalFunctionReadsTheOtherWayRound() {
        // As the simulator printed it on 2026-10-12, from a forced crash in a coroutine.
        val symbol = "kfun:com.luismejias.lumemedlink.app.CrashTour$1.\$invokeCOROUTINE$0.invokeSuspend#internal"
        val frame = framesOf("2 LumeMedLink 0x0 $symbol + 9")
        assertEquals(
            CrashFrame("com.luismejias.lumemedlink.app.CrashTour$1.\$invokeCOROUTINE$0", "invokeSuspend"),
            frame,
        )
    }

    @Test
    fun aNativeFrameIsNotOurs() {
        assertNull(framesOf("7   UIKitCore   0x00000001a2b3   -[UIApplication sendEvent:] + 300"))
    }

    @Test
    fun theStackOfARealThrowableParses() {
        val frames = IllegalStateException("x").platformFrames()
        // A test binary may be symbolised or not; whatever is found must be well formed.
        frames.forEach { check(it.className.isNotBlank() && it.functionName.isNotBlank()) }
    }
}
