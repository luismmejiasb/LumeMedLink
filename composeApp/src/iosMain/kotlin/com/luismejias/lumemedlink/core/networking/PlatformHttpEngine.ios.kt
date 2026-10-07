package com.luismejias.lumemedlink.core.networking

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.DarwinClientEngineConfig
import platform.Foundation.NSURLRequestReloadIgnoringCacheData
import platform.Foundation.NSURLSessionConfiguration

/**
 * Darwin (NSURLSession) engine, with every persistent store the session would share with the
 * system switched OFF (F12, ADR-0016 and its 2026-10-07 amendment).
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
 * this app's TLS on iOS. Pinning is ONE of two ways to close it; the other is classifying the
 * trust anchor in this engine's own `handleChallenge`, which is cheaper and needs no
 * certificate — evidence simulator-only, unconfirmed on device (ADR-0017 Part 2). Neither is
 * implemented today.
 */
internal actual fun platformHttpEngine(): HttpClientEngine = lumeDarwinEngine()

/**
 * The production engine, built in ONE place so the test that inspects the `NSURLSession` it really
 * produces builds it the same way production does (task 0004).
 *
 * [addition] exists for that test alone and production passes nothing. Ktor chains session blocks
 * in registration order, so an addition that called `configureSession` WOULD run after the posture
 * and could override it — which is why the test adds only a challenge handler: the one door through
 * which the live session, and therefore its real configuration, can be observed from outside Ktor.
 * `check-network-posture.sh` requires production to call this with no argument.
 */
internal fun lumeDarwinEngine(addition: DarwinClientEngineConfig.() -> Unit = {}): HttpClientEngine = Darwin.create {
    configureSession { applyLumeSessionPosture() }
    addition()
}

/**
 * The session posture, as a named seam a test and a gate can both point at.
 *
 * - `setURLCache(null)` is the load-bearing line for F12. The cache policy is belt-and-braces: it
 *   stops reads, and it is what a reader mistakes for the whole control — keeping both together
 *   with this comment is how the next person avoids deleting the line that matters.
 * - `setURLCredentialStorage(null)`: the default is the SHARED `NSURLCredentialStorage`, a
 *   persistent store of the system's that nothing here had ever said no to. No HTTP authentication
 *   fills it today; the line makes "never" the configuration rather than the current usage.
 * - `setHTTPCookieStorage(null)` and `setHTTPShouldSetCookies(false)`: Ktor 3.5.2 already nulls the
 *   cookie store before this block runs (visible in its `createSession`), so these two are not a
 *   fix — they move the guarantee from a detail inside a dependency into this file, where an
 *   upgrade cannot remove it silently. In this family a default is not a decision.
 *
 * Whether a Ktor upgrade still lets this block have the last word is what
 * `DarwinSessionPostureTest` measures, on the session the real engine hands NSURLSession.
 */
internal fun NSURLSessionConfiguration.applyLumeSessionPosture() {
    setURLCache(null)
    setRequestCachePolicy(NSURLRequestReloadIgnoringCacheData)
    setURLCredentialStorage(null)
    setHTTPCookieStorage(null)
    setHTTPShouldSetCookies(false)
}
