package com.luismejias.lumemedlink.core.telemetry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.experimental.ExperimentalNativeApi

/**
 * Firebase as the Swift host offers it (ADR-0052 §8). The SDK is a SwiftPM package of the Xcode project, which Kotlin
 * cannot import, so Swift implements this and hands it to `MainViewController` — or hands nothing, when the app was
 * built without `GoogleService-Info.plist`. Public because Swift must see it; plain types because the catalog is
 * enforced on this side, in [BridgedTelemetry], before a name ever reaches Swift.
 */
public interface FirebaseBridge {
    public fun setCollectionEnabled(enabled: Boolean)

    /** A catalog name and its details, already reduced to enum names on this side ([analyticsValues]). */
    public fun logEvent(name: String, parameters: Map<String, String>)

    public fun flag(key: String, defaultValue: Boolean): Boolean

    /** Fetch and activate, fire and forget: the values serve the next read. */
    public fun refreshFlags()

    /** An unhandled Kotlin exception, as Crashlytics may see it: the type and the frames, no reason (§4). */
    public fun recordCrash(typeName: String, frames: List<String>)
}

internal val LocalFirebaseBridge: ProvidableCompositionLocal<FirebaseBridge?> = staticCompositionLocalOf { null }

@Composable
internal actual fun rememberTelemetry(): Telemetry {
    val bridge = LocalFirebaseBridge.current
    return remember(bridge) { bridge?.let(::BridgedTelemetry) ?: UnconfiguredTelemetry }
}

private class BridgedTelemetry(private val bridge: FirebaseBridge) : Telemetry {
    init {
        installCrashHook(bridge)
    }

    override fun setCollectionEnabled(enabled: Boolean) = bridge.setCollectionEnabled(enabled)

    override fun log(event: AnalyticsEvent, details: Map<AnalyticsParam, Enum<*>>) =
        bridge.logEvent(event.eventName, analyticsValues(details))

    override fun flag(flag: RemoteFlag): Boolean = bridge.flag(flag.key, flag.default)

    override suspend fun refreshFlags() = bridge.refreshFlags()
}

/**
 * Kotlin/Native's frames read like `3 LumeMedLink 0x… kfun:com.luismejias.lumemedlink.app#App(){} + 123`: the class
 * (or the package, for a top-level function) before `#`, the function after it, and no line number. An internal
 * function reads `kfun:pkg.Class.function#internal` instead.
 */
private val KFUN = Regex("""kfun:([A-Za-z0-9_.$]+)#([A-Za-z0-9_<>$-]+)""")

/** One Kotlin frame as `Class.function`, or null for a native one: Crashlytics reads those itself. */
internal fun frameOf(line: String): String? = KFUN.find(line)?.let { match ->
    val (owner, after) = match.destructured
    // An internal function is mangled the other way round — `kfun:pkg.Class.function#internal` — measured on the
    // simulator on 2026-10-12: the function is the last segment before `#`, and «internal» is not its name.
    if (after == "internal" && '.' in owner) owner else "$owner.$after"
}

@OptIn(ExperimentalNativeApi::class)
internal fun Throwable.kotlinFrames(): List<String> = getStackTrace().mapNotNull(::frameOf)

/** Set once, so a second install does not wrap the hook around itself. Process-wide by nature, like the hook. */
@OptIn(ExperimentalNativeApi::class)
private fun installCrashHook(bridge: FirebaseBridge) {
    val previous = getUnhandledExceptionHook()
    if (previous is RecordingHook) return
    setUnhandledExceptionHook(RecordingHook(previous, bridge))
}

/**
 * Records, then ends the process as Kotlin would have. Crashlytics then also sees the abort as a native crash: one
 * Kotlin crash shows as two entries, the second without Kotlin frames — declared in ADR-0052, not hidden.
 */
@OptIn(ExperimentalNativeApi::class)
private class RecordingHook(private val previous: ReportUnhandledExceptionHook?, private val bridge: FirebaseBridge) :
    ReportUnhandledExceptionHook {
    override fun invoke(throwable: Throwable) {
        try {
            bridge.recordCrash(throwable::class.qualifiedName ?: "", throwable.kotlinFrames())
        } finally {
            if (previous != null) previous.invoke(throwable) else terminateWithUnhandledException(throwable)
        }
    }
}
