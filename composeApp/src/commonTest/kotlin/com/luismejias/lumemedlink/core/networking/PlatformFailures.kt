package com.luismejias.lumemedlink.core.networking

/** What the platform's engine throws when the device has no network at all. */
internal expect fun noNetworkFailure(): Throwable

/** What it throws when the network is there and the server refuses the connection (a backend that is down). */
internal expect fun refusedConnectionFailure(): Throwable
