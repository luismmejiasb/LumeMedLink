package com.luismejias.lumemedlink.core.session

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class RecordingStore : SecureStore {
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

/**
 * The logout contract at the level where it can be exhaustive (F5, ADR-0014).
 *
 * This test iterates [SecureStoreKey.entries], so a secret declared in a future slice is covered
 * the moment it is added to the enum — nobody has to remember to extend this file, and that is the
 * whole reason the keys stopped being loose constants.
 */
class SecureStoreWipeTest {

    @Test
    fun wipeRemovesEverySecretTheAppCanPersist() = runTest {
        val store = RecordingStore()
        SecureStoreKey.entries.forEach { key ->
            store.put(key.storageKey, "synthetic-value-for-${key.name}")
        }
        assertEquals(SecureStoreKey.entries.size, store.entries.size, "precondition: all written")

        store.wipe()

        SecureStoreKey.entries.forEach { key ->
            assertNull(store.get(key.storageKey), "${key.name} survived the wipe")
        }
    }

    @Test
    fun everyDeclaredKeyIsDistinct() {
        // Two secrets sharing a storage key would silently overwrite each other, and clearing one
        // would clear the other — a bug that looks like a working logout.
        val storageKeys = SecureStoreKey.entries.map { it.storageKey }
        assertEquals(storageKeys.size, storageKeys.toSet().size, "duplicate storage keys: $storageKeys")
    }

    @Test
    fun storageKeysAreVersioned() {
        // A versioned key lets a future format change land without reading a stale value as if it
        // were the new shape.
        SecureStoreKey.entries.forEach { key ->
            assertTrue(
                Regex(".*_v\\d+$").matches(key.storageKey),
                "${key.name} must carry a version suffix, got '${key.storageKey}'",
            )
        }
    }
}
