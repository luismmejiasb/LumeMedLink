package com.luismejias.lumemedlink.core.networking

/**
 * What the stack may speak (ADR-0004, ADR-0062). [HttpsOnly] everywhere but one place: a debug build talking to the
 * platform on the developer's own computer, which runs over plain http and nowhere else.
 */
internal sealed interface TransportPolicy {
    /** https, and the request's origin pinned to the base URL's. The only policy a release build ever builds. */
    data object HttpsOnly : TransportPolicy

    /**
     * Plain http to the local backend, from a debug build only (ADR-0062): the host app chooses it by build type, never
     * shared code. Even with it, only [LOCAL_BACKEND_HOSTS] are reachable, so a typo in a URL cannot open cleartext
     * toward a network.
     */
    data object LocalCleartext : TransportPolicy
}

/**
 * The computer the app is developed on, as each runtime names it: itself (`localhost`, `127.0.0.1`, reached from a
 * phone through `adb reverse`), and the Android emulator's alias for its host (`10.0.2.2`). No LAN address.
 */
internal val LOCAL_BACKEND_HOSTS: Set<String> = setOf("localhost", "127.0.0.1", "10.0.2.2")

/**
 * Whether a base URL of [scheme] and [host] may be built under [policy]: https always, and http only toward a local
 * host and only under [TransportPolicy.LocalCleartext].
 */
internal fun transportAllows(policy: TransportPolicy, scheme: String, host: String): Boolean = when (scheme) {
    "https" -> true
    "http" -> policy == TransportPolicy.LocalCleartext && host in LOCAL_BACKEND_HOSTS
    else -> false
}
