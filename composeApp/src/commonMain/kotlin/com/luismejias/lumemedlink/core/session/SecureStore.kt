package com.luismejias.lumemedlink.core.session

/**
 * The tier-1 secret store seam (ADR-0005): hardware-backed on each platform — Keychain on iOS,
 * Keystore-wrapped storage on Android (ADR-0009). Everything behind it is readable WITHOUT a
 * biometric prompt (silent refresh must work); the tier-2 unlock secret is NOT this seam and
 * arrives with the shell's biometric gate.
 *
 * [wipe] removes this app's OWN namespace only — never a sweep of the platform store (family
 * rule: purging other software's entries is vandalism, and on iOS a synchronizable sweep would
 * propagate the deletion to the user's other devices).
 */
internal interface SecureStore {
    suspend fun put(key: String, value: String)

    suspend fun get(key: String): String?

    suspend fun remove(key: String)

    /** Removes every entry this app owns. Part of the logout contract (§8.13). */
    suspend fun wipe()
}

/** Keys are constants so a typo cannot silently create a second secret. */
internal object SecureStoreKeys {
    const val SESSION_TOKENS: String = "session_tokens_v1"
}
