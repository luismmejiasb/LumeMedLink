package com.luismejias.lumemedlink.core.crash

import com.luismejias.lumemedlink.core.session.SecureStore
import com.luismejias.lumemedlink.core.session.SecureStoreKey
import com.luismejias.lumemedlink.core.session.SecureStoreUnreadableException
import kotlin.random.Random

private const val GRANTED = "granted"
private const val KEY_BYTES = 16

/**
 * The person's optional consent to send crash reports with a crash-only key, and that key (ADR-0051 §3–4).
 *
 * Both live in the secure store, so the logout erases them with everything else (§8.13): on a shared phone the next
 * person starts without the last one's consent or key. The key is random and means nothing on its own — it is not a
 * secret and not the phone-binding key — so it is made with the ordinary generator; what it must be is unlinked.
 */
internal class CrashConsent(private val store: SecureStore, private val newKey: () -> String = ::randomCrashKey) {
    /** Whether the person consented. An unreadable store reads as "no": never send a key on doubt. */
    @Suppress("SwallowedException")
    suspend fun granted(): Boolean = try {
        store.get(SecureStoreKey.CRASH_CONSENT.storageKey) == GRANTED
    } catch (unreadable: SecureStoreUnreadableException) {
        false
    }

    /** Granting keeps (or makes) the key; withdrawing deletes it, and the next consent makes a new one. */
    suspend fun setGranted(granted: Boolean) {
        if (granted) {
            store.put(SecureStoreKey.CRASH_CONSENT.storageKey, GRANTED)
            if (keyOrNull() == null) store.put(SecureStoreKey.CRASH_KEY.storageKey, newKey())
        } else {
            store.remove(SecureStoreKey.CRASH_CONSENT.storageKey)
            store.remove(SecureStoreKey.CRASH_KEY.storageKey)
        }
    }

    /** The key to attach to a report being SENT now, or `null` without consent (ADR-0051 §3). */
    suspend fun keyForSending(): String? = if (granted()) keyOrNull() else null

    @Suppress("SwallowedException")
    private suspend fun keyOrNull(): String? = try {
        store.get(SecureStoreKey.CRASH_KEY.storageKey)
    } catch (unreadable: SecureStoreUnreadableException) {
        null
    }
}

private fun randomCrashKey(): String =
    Random.nextBytes(KEY_BYTES).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
