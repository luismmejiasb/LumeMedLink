package com.luismejias.lumemedlink.core.networking

import java.net.ConnectException
import java.net.UnknownHostException

internal actual fun noNetworkFailure(): Throwable = UnknownHostException("synthetic")

internal actual fun refusedConnectionFailure(): Throwable = ConnectException("synthetic")
