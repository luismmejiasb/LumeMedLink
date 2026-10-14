package com.luismejias.lumemedlink.app

/**
 * Where the app's data comes from, chosen by the host app by build type, never by shared code (ADR-0062).
 *
 * [Script] is the design's script and no network: every release build until the platform is deployed (ADR-0058 point
 * 5). [localBackend] is the platform on the developer's own computer, over plain http: only a debug build passes it,
 * and the stack refuses it toward anything but a local host.
 */
public class AppEnvironment private constructor(internal val localBackend: String?) {
    override fun toString(): String = if (localBackend == null) "AppEnvironment(script)" else "AppEnvironment(local)"

    public companion object {
        public val Script: AppEnvironment = AppEnvironment(null)

        /** The platform on this computer, as the device names it: `http://10.0.2.2:3000` from the Android emulator. */
        public fun localBackend(baseUrl: String): AppEnvironment = AppEnvironment(baseUrl)
    }
}
