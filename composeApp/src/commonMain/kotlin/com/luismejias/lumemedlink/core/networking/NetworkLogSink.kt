package com.luismejias.lumemedlink.core.networking

/**
 * One network log line, redacted BY TYPE: these four fields are everything the stack is able to
 * log — no headers, no body, no query (§8.1). [path] is the encoded path only; the stack strips
 * the query before constructing the entry, and the test asserts it on captured output.
 *
 * Format mirror of LumeMed's log line: `GET /v1/agenda status=200 rid=…`.
 */
internal data class NetworkLogEntry(val method: String, val path: String, val status: Int?, val rid: String) {
    override fun toString(): String = "$method $path status=${status ?: "-"} rid=$rid"
}

/**
 * Seam for the redacting log facade (its real implementation arrives with the session slice; the
 * stack must not know where log lines go, only what shape they are allowed to have).
 */
internal interface NetworkLogSink {
    fun log(entry: NetworkLogEntry)
}
