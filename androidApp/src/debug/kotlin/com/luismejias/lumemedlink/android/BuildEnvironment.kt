package com.luismejias.lumemedlink.android

import com.luismejias.lumemedlink.app.AppEnvironment

/**
 * A debug build talks to the platform on the developer's computer (ADR-0062). `10.0.2.2` is how the Android emulator
 * names it; a physical phone reaches it as `localhost` through `adb reverse tcp:3000 tcp:3000`, with
 * `-Plume.localBackend=http://localhost:3000`. Release has its own file, and no URL.
 */
internal fun buildEnvironment(): AppEnvironment = AppEnvironment.localBackend(BuildConfig.LUME_LOCAL_BACKEND)
