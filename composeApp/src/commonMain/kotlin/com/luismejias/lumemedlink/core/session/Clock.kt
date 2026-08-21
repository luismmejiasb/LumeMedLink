package com.luismejias.lumemedlink.core.session

/** Injectable time (§6: testable without timing). Production wires [SystemClock]. */
internal fun interface Clock {
    fun nowEpochMillis(): Long
}

internal object SystemClock : Clock {
    override fun nowEpochMillis(): Long = epochMillisNow()
}

/** Platform wall clock (ADR-0008: expect/actual lives in core/ only). */
internal expect fun epochMillisNow(): Long
