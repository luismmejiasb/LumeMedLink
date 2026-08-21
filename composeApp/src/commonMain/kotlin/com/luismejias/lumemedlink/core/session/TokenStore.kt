package com.luismejias.lumemedlink.core.session

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Persists the session's token pair as ONE entry in the tier-1 [SecureStore]. A corrupt or
 * unreadable entry loads as `null` — no session — because a lock that cannot tell whether it
 * should close, closes (the family's fail-closed direction).
 */
internal class TokenStore(private val secureStore: SecureStore) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): SessionTokens? {
        val raw = secureStore.get(SecureStoreKey.SESSION_TOKENS.storageKey) ?: return null
        return try {
            json.decodeFromString<SessionTokens>(raw)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    suspend fun save(tokens: SessionTokens) {
        secureStore.put(
            SecureStoreKey.SESSION_TOKENS.storageKey,
            json.encodeToString(SessionTokens.serializer(), tokens),
        )
    }

    /** Part of the logout contract (§8.13): the disk half. */
    suspend fun clear() {
        secureStore.remove(SecureStoreKey.SESSION_TOKENS.storageKey)
    }
}
