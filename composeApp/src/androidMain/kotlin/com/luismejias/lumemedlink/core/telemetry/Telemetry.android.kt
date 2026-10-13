package com.luismejias.lumemedlink.core.telemetry

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Firebase starts here, when the app says so, and only if the build carried its configuration (ADR-0052 §8): the
 * options come from the string resources `androidApp` generates out of a `google-services.json` kept outside git.
 * Without them [FirebaseOptions.fromResource] answers null and nothing of Firebase ever runs.
 * `FirebaseInitProvider` is removed from the manifest, so nothing started it earlier either.
 */
@Composable
internal actual fun rememberTelemetry(): Telemetry {
    val context = LocalContext.current.applicationContext
    return remember { firebaseTelemetry(context) ?: UnconfiguredTelemetry }
}

private fun firebaseTelemetry(context: Context): Telemetry? {
    val options = FirebaseOptions.fromResource(context) ?: return null
    if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context, options)
    return FirebaseTelemetry(context)
}

/** A detail as Firebase takes it, or null for none. Built only from [analyticsValues]: enum names, never data. */
private fun bundleOf(details: Map<AnalyticsParam, Enum<*>>): Bundle? = analyticsValues(details)
    .takeIf { it.isNotEmpty() }
    ?.let { values -> Bundle().apply { values.forEach { (key, value) -> putString(key, value) } } }

private class FirebaseTelemetry(context: Context) : Telemetry {
    private val crashlytics = FirebaseCrashlytics.getInstance()
    private val analytics = FirebaseAnalytics.getInstance(context)
    private val config = FirebaseRemoteConfig.getInstance().apply {
        setDefaultsAsync(RemoteFlag.entries.associate { it.key to it.default })
    }

    init {
        // After Crashlytics put its own handler in place, so this one runs first and hands it the message-free copy.
        installMessageFreeCrashes()
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        crashlytics.isCrashlyticsCollectionEnabled = enabled
        analytics.setAnalyticsCollectionEnabled(enabled)
        if (!enabled) {
            // Crashlytics keeps what it caught while collection was off; without consent it must not wait to be sent.
            crashlytics.deleteUnsentReports()
            analytics.resetAnalyticsData()
        }
    }

    override fun log(event: AnalyticsEvent, details: Map<AnalyticsParam, Enum<*>>) =
        analytics.logEvent(event.eventName, bundleOf(details))

    override fun flag(flag: RemoteFlag): Boolean = config.getBoolean(flag.key)

    override suspend fun refreshFlags() {
        suspendCancellableCoroutine { done -> config.fetchAndActivate().addOnCompleteListener { done.resume(Unit) } }
    }
}
