package com.luismejias.lumemedlink.core.networking

import java.net.UnknownHostException

/**
 * Without a network OkHttp cannot resolve the host, and that is how Android says it. A `ConnectException` (refused)
 * and a timeout are left out on purpose: the device reached the network and the server did not answer.
 */
internal actual fun Throwable.meansNoNetwork(): Boolean =
    generateSequence(this) { it.cause }.any { it is UnknownHostException }
