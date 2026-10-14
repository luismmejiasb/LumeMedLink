package com.luismejias.lumemedlink.core.features

import com.luismejias.lumemedlink.core.telemetry.RemoteFlag
import com.luismejias.lumemedlink.core.telemetry.RemoteFlags
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the role comes from (ADR-0060 §3). Today the access flow's answer; `/v1/me` once the backend has it. */
internal interface RoleSource {
    val role: StateFlow<RoleState>
}

/** Where the flags come from (ADR-0060 §3). */
internal interface FlagSource {
    val flags: StateFlow<FlagValues>

    /** Asks for fresh values. Never throws for a failed fetch: the last values stay. */
    suspend fun refresh()
}

/** A snapshot of every flag. A flag it has no value for answers its compiled default. */
internal data class FlagValues(private val values: Map<RemoteFlag, Boolean> = emptyMap()) {
    operator fun get(flag: RemoteFlag): Boolean = values[flag] ?: flag.default
}

/**
 * The role as the session knows it: set when somebody is let in, and [Unknown][RoleState.Unknown] again at sign-out,
 * before anything else of the sign-out runs, so no role-gated feature outlives the session for a frame.
 */
internal class SessionRoleSource : RoleSource {
    private val current = MutableStateFlow<RoleState>(RoleState.Unknown)
    override val role: StateFlow<RoleState> = current.asStateFlow()

    fun signedIn(role: Role) {
        current.value = RoleState.Known(role)
    }

    fun signedOut() {
        current.value = RoleState.Unknown
    }
}

/** The flags as Remote Config serves them, read whole after each refresh so a screen never sees half of a fetch. */
internal class RemoteConfigFlagSource(private val remote: RemoteFlags) : FlagSource {
    private val current = MutableStateFlow(read())
    override val flags: StateFlow<FlagValues> = current.asStateFlow()

    override suspend fun refresh() {
        try {
            remote.refreshFlags()
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") failure: Exception) {
            // A failed fetch keeps the last values (ADR-0060 §2) — unless the caller itself was cancelled, which must
            // propagate (ADR-0026).
            currentCoroutineContext().ensureActive()
            return
        }
        current.value = read()
    }

    private fun read(): FlagValues = FlagValues(RemoteFlag.entries.associateWith(remote::flag))
}
