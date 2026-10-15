package com.luismejias.lumemedlink.core.networking

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSURLErrorDataNotAllowed
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorInternationalRoamingOff
import platform.Foundation.NSURLErrorNotConnectedToInternet

/**
 * NSURLSession's own «no network» codes. `NSURLErrorCannotConnectToHost` (a refused connection) and the timeouts are
 * left out on purpose: the device reached the network and the server did not answer.
 */
internal actual fun Throwable.meansNoNetwork(): Boolean = generateSequence(this) { it.cause }
    .filterIsInstance<DarwinHttpRequestException>()
    .any { it.origin.domain == NSURLErrorDomain && it.origin.code in NO_NETWORK_CODES }

private val NO_NETWORK_CODES = setOf(
    NSURLErrorNotConnectedToInternet,
    NSURLErrorDataNotAllowed,
    NSURLErrorInternationalRoamingOff,
)
