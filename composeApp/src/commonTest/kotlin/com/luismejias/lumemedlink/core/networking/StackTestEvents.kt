package com.luismejias.lumemedlink.core.networking

import com.luismejias.lumemedlink.core.security.SecurityEventEmitter
import com.luismejias.lumemedlink.core.security.SecurityEventKind

/** A stack built for a test that is not about its security events. */
internal val NO_SECURITY_EVENTS: SecurityEventEmitter = SecurityEventEmitter { }

/** What a stack told the security channel, in order. */
internal class RecordedSecurityEvents : SecurityEventEmitter {
    val kinds = mutableListOf<SecurityEventKind>()

    override fun emit(kind: SecurityEventKind) {
        kinds += kind
    }
}
