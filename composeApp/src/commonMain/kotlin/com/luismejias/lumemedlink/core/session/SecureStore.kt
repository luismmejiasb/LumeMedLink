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

/**
 * Every secret this app can persist, as an ENUM rather than loose constants (F5, ADR-0014).
 *
 * The reason is the logout contract: `SecureStoreWipeTest` writes a value under **every entry
 * here** and asserts the wipe removes all of them. Because the set is enumerable, a secret added
 * in some future slice is covered by that test the moment it is declared — nobody has to remember
 * to extend the test, which is precisely the kind of remembering that fails.
 */
internal enum class SecureStoreKey(val storageKey: String) {
    /** The clinician's access + refresh pair (ADR-0003). */
    SESSION_TOKENS("session_tokens_v1"),

    /** The challenge the tier-2 unlock key signs (ADR-0011). */
    UNLOCK_CHALLENGE("unlock_challenge_v1"),
}
