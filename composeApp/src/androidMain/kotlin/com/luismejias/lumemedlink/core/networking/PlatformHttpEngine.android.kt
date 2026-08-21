package com.luismejias.lumemedlink.core.networking

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.ConnectionSpec
import okhttp3.TlsVersion

/**
 * OkHttp engine with TLS >= 1.2 EXPLICIT (§7: a default is not a decision). No cleartext spec is
 * present at all, so the engine refuses plain http at its own layer too — the stack already
 * failed closed before reaching here; this is depth, not the gate.
 */
internal actual fun platformHttpEngine(): HttpClientEngine = OkHttp.create {
    config {
        connectionSpecs(
            listOf(
                ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
                    .build(),
            ),
        )
    }
}
