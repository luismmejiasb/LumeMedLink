package com.luismejias.lumemedlink.core.care

/**
 * A read a screen can do without (ADR-0067): it is not the operation the screen exists for, so its failure is never
 * the screen's. It answers the VALUE, never a [CareResult] — so it cannot be handed to `allOf`, and cannot turn the
 * screen into its error state. A failure gives [fallback], and the screen leaves out what it would have drawn.
 */
internal suspend fun <T> secondary(fallback: T, read: suspend () -> CareResult<T>): T = when (val answer = read()) {
    is CareResult.Ok -> answer.value
    is CareResult.Failed -> fallback
}

/**
 * A [secondary] read that runs in the background, behind a screen that is not its own — the bell's count on Inicio
 * (the author, 2026-10-15). After [MAX_SECONDARY_MISSES] failures in a row it stops asking: a background read is never
 * retried without end. A success starts the count again. Its own screen reads it with its own failure and retry.
 */
internal class CappedSecondaryRead<T>(private val fallback: T, private val read: suspend () -> CareResult<T>) {
    private var misses = 0

    suspend fun get(): T {
        if (misses >= MAX_SECONDARY_MISSES) return fallback
        return when (val answer = read()) {
            is CareResult.Ok -> answer.value.also { misses = 0 }
            is CareResult.Failed -> fallback.also { misses++ }
        }
    }
}

internal const val MAX_SECONDARY_MISSES = 3
