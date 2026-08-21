package com.luismejias.lumemedlink.core.networking

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSURLRequestReloadIgnoringCacheData
import platform.Foundation.NSURLSessionConfiguration

/**
 * Darwin (NSURLSession) engine, with the URL cache DISABLED (F12, ADR-0016).
 *
 * Why that is not a nicety: `defaultSessionConfiguration` carries `URLCache.shared`, a 20 MB
 * disk-backed cache, and NSURLCache stores the **request headers alongside the response body**. So
 * an ordinary HTTPS GET through the default configuration writes, in plaintext, to
 * `Library/Caches`: the agenda or roster body, and the `Authorization: Bearer …` header this stack
 * attaches. Both survive logout, which clears the Keychain namespace and knows nothing about a URL
 * cache (ADR-0014).
 *
 * The subtlety that made this easy to miss: Ktor already sets
 * `NSURLRequestReloadIgnoringLocalCacheData` on every request, which reads as "no caching". It is
 * not — that policy suppresses cache READS. Writes continue. Only removing the cache stops the
 * write, and it is verified that an HTTPS GET with no `Cache-Control` at all is stored.
 *
 * TLS floor is ATS's: the host app ships with NO ATS exceptions (§7), verified on device when the
 * iOS host exists. Declared asymmetry (threat model): **iOS trusts user-installed root CAs and
 * Android does not**, so a device with an attacker's or an employer's CA profile can intercept
 * this app's TLS on iOS. Pinning — deferred by ADR-0004 — is the only thing that would close it.
 */
internal actual fun platformHttpEngine(): HttpClientEngine = Darwin.create {
    configureSession { applyLumeCachePosture() }
}

/**
 * The cache posture, as a named seam so a test and a gate can both point at it.
 *
 * `setURLCache(null)` is the load-bearing line. The policy is belt-and-braces: it stops reads, and
 * it is what a reader mistakes for the whole control — keeping both together with this comment is
 * how the next person avoids deleting the line that matters.
 */
internal fun NSURLSessionConfiguration.applyLumeCachePosture() {
    setURLCache(null)
    setRequestCachePolicy(NSURLRequestReloadIgnoringCacheData)
}
