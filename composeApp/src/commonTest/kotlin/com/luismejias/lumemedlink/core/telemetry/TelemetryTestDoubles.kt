package com.luismejias.lumemedlink.core.telemetry

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

/** Remembers what the app told Firebase, in order. */
internal class RecordedTelemetry : Telemetry {
    val collection = mutableListOf<Boolean>()
    val events = mutableListOf<AnalyticsEvent>()

    override fun setCollectionEnabled(enabled: Boolean) {
        collection += enabled
    }

    override fun log(event: AnalyticsEvent) {
        events += event
    }

    override fun flag(flag: RemoteFlag): Boolean = flag.default

    override suspend fun refreshFlags() = Unit
}
