package com.luismejias.lumemedlink.core.crash

import com.luismejias.lumemedlink.core.session.SecureStore

/** A secure store in a map, for tests that need the logout's namespace and nothing else. */
internal class MemorySecureStore : SecureStore {
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

/** The queue's files in a map. */
internal class MemoryCrashFileStore : CrashFileStore {
    val files = linkedMapOf<String, String>()

    override fun write(name: String, text: String) {
        files[name] = text
    }

    override fun read(name: String): String? = files[name]

    override fun delete(name: String) {
        files.remove(name)
    }

    override fun names(): List<String> = files.keys.toList()
}

internal val TEST_IDENTITY =
    AppIdentity(version = "0.1.0", build = "1", platform = CrashPlatform.ANDROID, osVersion = "16")
