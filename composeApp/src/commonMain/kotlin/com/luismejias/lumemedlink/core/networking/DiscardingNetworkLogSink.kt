package com.luismejias.lumemedlink.core.networking

/**
 * Where network log lines go until the logging facade has a destination (§8.1): nowhere. The default is silence, as
 * for every other log of this app; the redaction happens before this, so a destination added later inherits it.
 */
internal object DiscardingNetworkLogSink : NetworkLogSink {
    override fun log(entry: NetworkLogEntry) = Unit
}
