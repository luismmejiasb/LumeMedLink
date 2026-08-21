package com.luismejias.lumemedlink.core.session

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenStoreTest {

    private class MapStore : SecureStore {
        val entries = mutableMapOf<String, String>()

        override suspend fun put(key: String, value: String) {
            entries[key] = value
        }

        override suspend fun get(key: String): String? = entries[key]

        override suspend fun remove(key: String) {
            entries.remove(key)
        }

        override suspend fun wipe() {
            entries.clear()
        }
    }

    @Test
    fun roundTrip() = runTest {
        val store = TokenStore(MapStore())
        val pair = SessionTokens("acc-t", "ref-t", 42L)
        store.save(pair)
        assertEquals(pair, store.load())
    }

    @Test
    fun emptyStoreLoadsNull() = runTest {
        assertNull(TokenStore(MapStore()).load())
    }

    @Test
    fun corruptEntryLoadsNull() = runTest {
        val backing = MapStore()
        backing.entries[SecureStoreKey.SESSION_TOKENS.storageKey] = "{not tokens at all"
        assertNull(TokenStore(backing).load(), "fail closed: unreadable means no session")
    }

    @Test
    fun clearRemovesTheEntry() = runTest {
        val backing = MapStore()
        val store = TokenStore(backing)
        store.save(SessionTokens("acc-t", "ref-t", 42L))
        store.clear()
        assertTrue(backing.entries.isEmpty())
    }

    @Test
    fun tokensNeverAppearInToString() {
        val pair = SessionTokens("acc-secret-xyz", "ref-secret-uvw", 42L)
        val printed = pair.toString()
        assertFalse(printed.contains("acc-secret-xyz"))
        assertFalse(printed.contains("ref-secret-uvw"))
    }
}
