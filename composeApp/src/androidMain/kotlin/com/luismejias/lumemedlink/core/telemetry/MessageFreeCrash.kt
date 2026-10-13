package com.luismejias.lumemedlink.core.telemetry

private const val MAX_CAUSES = 8

/**
 * A crash as Crashlytics may see it (ADR-0052 §4): the original type's name and its frames, and no message at any
 * depth. Messages are where personal data hides — one already carried a RUT inside a URL (ADR-0016) — while a type
 * name and a frame are code.
 */
internal class MessageFreeCrash(originalType: String, cause: Throwable?) : RuntimeException(originalType, cause)

internal fun Throwable.withoutMessages(depth: Int = 0): Throwable {
    val next = cause?.takeIf { it !== this && depth < MAX_CAUSES }?.withoutMessages(depth + 1)
    return MessageFreeCrash(this::class.java.name, next).also { it.stackTrace = stackTrace }
}

/** Hands the next handler — Crashlytics', then the system's — the message-free copy, never the original. */
private class MessageFreeHandler(val next: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // Copying must not become the crash: a failure here would stand in for the first one (ADR-0051 §7).
        val copy = runCatching {
            throwable.withoutMessages()
        }.getOrElse { MessageFreeCrash(throwable::class.java.name, null) }
        next?.uncaughtException(thread, copy)
    }
}

/** Idempotent: installing twice does not wrap the handler around itself. */
internal fun installMessageFreeCrashes() {
    val current = Thread.getDefaultUncaughtExceptionHandler()
    if (current is MessageFreeHandler) return
    Thread.setDefaultUncaughtExceptionHandler(MessageFreeHandler(current))
}
