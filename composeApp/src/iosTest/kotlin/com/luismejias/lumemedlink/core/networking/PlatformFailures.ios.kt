package com.luismejias.lumemedlink.core.networking

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSError
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorNotConnectedToInternet

internal actual fun noNetworkFailure(): Throwable = darwinFailure(NSURLErrorNotConnectedToInternet)

internal actual fun refusedConnectionFailure(): Throwable = darwinFailure(NSURLErrorCannotConnectToHost)

private fun darwinFailure(code: Long): Throwable =
    DarwinHttpRequestException(NSError.errorWithDomain(NSURLErrorDomain, code, null))
