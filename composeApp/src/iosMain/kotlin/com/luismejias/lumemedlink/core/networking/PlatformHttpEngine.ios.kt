package com.luismejias.lumemedlink.core.networking

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

/**
 * Darwin (NSURLSession) engine. TLS floor is ATS's: the host app ships with NO ATS exceptions
 * (§7), which S1.2 verifies on device — the engine adds nothing to subtract.
 */
internal actual fun platformHttpEngine(): HttpClientEngine = Darwin.create()
