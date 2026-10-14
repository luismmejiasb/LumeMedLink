package com.luismejias.lumemedlink.core.telemetry

import com.luismejias.lumemedlink.core.session.SecureStore
import com.luismejias.lumemedlink.core.session.SecureStoreKey
import com.luismejias.lumemedlink.core.session.SecureStoreUnreadableException

private const val GRANTED = "granted"

/**
 * The person's optional consent to Crashlytics and Analytics (ADR-0052 §2), asked in 2b and withdrawn in Configuración.
 *
 * It lives in the secure store, so the logout erases it with everything else (§8.13): on a shared phone the next person
 * starts without the last one's choice. Every change reaches [telemetry] at once, and [restore] hands it the stored
 * choice at launch — both SDKs start off, by manifest and plist, until this says otherwise.
 */
internal class TelemetryConsent(private val store: SecureStore, private val telemetry: TelemetryCollection) {
    /** Whether the person consented. An unreadable store reads as "no": never collect on doubt. */
    @Suppress("SwallowedException")
    suspend fun granted(): Boolean = try {
        store.get(SecureStoreKey.TELEMETRY_CONSENT.storageKey) == GRANTED
    } catch (unreadable: SecureStoreUnreadableException) {
        false
    }

    suspend fun setGranted(granted: Boolean) {
        if (granted) {
            store.put(SecureStoreKey.TELEMETRY_CONSENT.storageKey, GRANTED)
        } else {
            store.remove(SecureStoreKey.TELEMETRY_CONSENT.storageKey)
        }
        telemetry.setCollectionEnabled(granted)
    }

    /** At launch, and after the logout's wipe: the SDKs follow what the store says now. */
    suspend fun restore() = telemetry.setCollectionEnabled(granted())
}
