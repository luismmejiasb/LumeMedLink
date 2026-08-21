package com.luismejias.lumemedlink.core.logging

/**
 * What a log line is allowed to say (F22, §8.1).
 *
 * A closed set of events, not a string. That is the whole design: a facade taking `String` is a
 * facade someone eventually hands a patient's name, and no reviewer catches it forever. Here there
 * is nowhere to put one — the same shape `PushSignal` uses for notifications (ADR-0012), for the
 * same reason.
 *
 * Anything that varies per call and could identify a person is either absent or an opaque
 * reference. `detail` exists for the small number of values that genuinely help and provably
 * cannot identify: a status code, a duration, an enum name.
 */
internal enum class LogEvent {
    SESSION_ESTABLISHED,
    SESSION_ENDED,
    SESSION_LOCKED,
    SESSION_UNLOCK_FAILED,
    SESSION_UNLOCK_UNAVAILABLE,
    TOKEN_REFRESH_STARTED,
    TOKEN_REFRESH_REJECTED,
    SECURE_STORE_UNREADABLE,
    NETWORK_ORIGIN_REFUSED,
    APP_AVAILABILITY_CHECKED,
}

/**
 * A number or an enum name — never free text, never a value that came from a person or a server.
 * The type exists so the facade's signature refuses prose at the call site rather than trusting
 * everyone to remember §8.1.
 */
@kotlin.jvm.JvmInline
internal value class LogDetail private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        fun of(number: Int): LogDetail = LogDetail(number.toString())

        fun of(millis: Long): LogDetail = LogDetail("${millis}ms")

        /** Only an enum CONSTANT name: uppercase, underscores, nothing a person could be in. */
        fun ofEnumName(name: String): LogDetail? =
            if (Regex("^[A-Z][A-Z0-9_]*$").matches(name)) LogDetail(name) else null
    }
}

/**
 * The ONE place this app writes a log line (§8.1, ADR-0020). `Scripts/check-logging.sh` and
 * detekt's ForbiddenImport keep every platform logging API unreachable outside `core/`, so a
 * second path cannot appear quietly.
 *
 * The network stack keeps its own [com.luismejias.lumemedlink.core.networking.NetworkLogSink]
 * because its line has a different, equally constrained shape; both are seams, and neither takes a
 * caller-supplied string.
 */
internal interface LumeLogSink {
    fun log(event: LogEvent, detail: LogDetail? = null)
}

/**
 * The default: write NOTHING. Not a placeholder — a decision.
 *
 * Anything written to logcat leaves the device inside a bug report the user can send to anyone, on
 * a release build, surviving a reboot (F22 finding). Until this app has a reason to log that
 * survives that fact, the honest implementation is silence, and the seam exists so the day a
 * reason appears there is exactly one file to change.
 */
internal object DiscardingLogSink : LumeLogSink {
    override fun log(event: LogEvent, detail: LogDetail?) = Unit
}
