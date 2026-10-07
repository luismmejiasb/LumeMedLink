package com.luismejias.lumemedlink.core.session

import com.luismejias.lumemedlink.core.networking.TokenProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/** A token this close to expiry is treated as expired: clock skew must not produce a 401 storm. */
private const val EXPIRY_SKEW_MILLIS = 30_000L

/**
 * The session: owns the token pair, implements the stack's [TokenProvider] seam, and is the ONE
 * place a refresh happens (ADR-0003).
 *
 * **Single-flight by construction:** every state transition happens under one [Mutex]. When N
 * callers hit an expired access token concurrently, the first refreshes while the rest wait on
 * the lock; by the time they enter, the fresh pair is in place and they return it without a
 * second network call. The test pins this (N concurrent -> exactly 1 refresh).
 *
 * **Fail directions**, per the family table: a refresh the server REJECTS (null) kills the
 * session — memory and disk wiped, next caller gets null and the UI sees AuthExpired. A refresh
 * that THROWS (transport) leaves state untouched — not refreshed, retry on the next call; the
 * caller in flight sees the exception.
 *
 * **Logout is a contract** (§8.13): memory + disk in one call, pinned by test. The platform
 * stores guarantee the "hardware" half (Keychain/Keystore entries are the disk half here).
 */
internal class SessionManager(
    private val tokenStore: TokenStore,
    private val refreshClient: RefreshClient,
    private val clock: Clock = SystemClock,
) : TokenProvider {
    private val mutex = Mutex()
    private var loaded = false
    private var tokens: SessionTokens? = null

    private val ended = MutableStateFlow(false)

    /**
     * True once the server has refused to refresh this session (task 0020). A rejected refresh erases
     * only the token entry — not a logout (ADR-0036 point 1) — and until 2026-10-07 the shell was not
     * told at all: the next request simply had no token. The shell watches this and returns to Login;
     * what the dead session left behind is erased by the next [establishSession] (ADR-0037).
     */
    val sessionEnded: StateFlow<Boolean> = ended.asStateFlow()

    // Written by the stack's response pipeline (any thread), read under the mutex.
    @Volatile
    private var rejected = false

    override suspend fun token(): String? = mutex.withLock {
        ensureLoadedLocked()
        val current = tokens ?: return@withLock null
        if (!rejected && !current.isExpiringSoon()) return@withLock current.accessToken
        refreshLocked(current)
    }

    override fun tokenWasRejected() {
        rejected = true
    }

    /**
     * Writes the pair the backend issued. Reached ONLY through [establishSession], which erases what an
     * earlier session left and makes the tier-2 material first (ADR-0037; the gate enforces it).
     */
    suspend fun establish(newTokens: SessionTokens): Unit = mutex.withLock {
        tokens = newTokens
        loaded = true
        rejected = false
        tokenStore.save(newTokens)
        ended.value = false
    }

    /** The wipe contract: memory + persisted entry, one call. */
    suspend fun logout(): Unit = mutex.withLock {
        tokens = null
        loaded = true
        rejected = false
        tokenStore.clear()
    }

    suspend fun hasSession(): Boolean = mutex.withLock {
        ensureLoadedLocked()
        tokens != null
    }

    private suspend fun ensureLoadedLocked() {
        if (!loaded) {
            tokens = tokenStore.load()
            loaded = true
        }
    }

    private suspend fun refreshLocked(current: SessionTokens): String? {
        val fresh = refreshClient.refresh(current.refreshToken)
        return if (fresh == null) {
            tokens = null
            rejected = false
            tokenStore.clear()
            ended.value = true
            null
        } else {
            tokens = fresh
            rejected = false
            tokenStore.save(fresh)
            fresh.accessToken
        }
    }

    private fun SessionTokens.isExpiringSoon(): Boolean =
        accessExpiresAtEpochMillis - EXPIRY_SKEW_MILLIS <= clock.nowEpochMillis()
}
