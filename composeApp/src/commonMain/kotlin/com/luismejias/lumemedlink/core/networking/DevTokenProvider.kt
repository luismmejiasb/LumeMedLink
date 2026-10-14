package com.luismejias.lumemedlink.core.networking

import com.luismejias.lumemedlink.core.networking.contract.ContractOperations
import com.luismejias.lumemedlink.core.networking.contract.DevTokenRequestDto
import com.luismejias.lumemedlink.core.networking.contract.DevTokenRequestDtoClient
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The patient the platform's seed creates for local work (`apps/api/src/dev/seed-patient-tier.ts`). Synthetic. Its
 * twin `dev-doctor-link` opens the doctor's side.
 */
internal const val DEV_PATIENT = "dev-patient"

/**
 * The local platform's dev token, for a debug build's local environment only (ADR-0062): never Identity Platform,
 * never a real person (§9). It lives in memory and is never written anywhere: a dev token is not a session (ADR-0059
 * governs those), and the next launch asks again.
 *
 * [contract] must be built over a stack WITHOUT a token provider: this IS the token provider, and asking for a token
 * to fetch the token would wait on itself (the deadlock `lumeHttpClient` documents for a refresh client).
 */
internal class DevTokenProvider(
    private val contract: ContractClient,
    private val subject: String = DEV_PATIENT,
    /** LumeMedLink's one IdP tenant (the platform's answer `0074` to request `0011`, contract 0.128.0). */
    private val client: DevTokenRequestDtoClient = DevTokenRequestDtoClient.LUMEMEDLINK,
) : TokenProvider {
    private val current = MutableStateFlow<String?>(null)
    private val fetching = Mutex()

    /** The token, asked for once and shared: concurrent first requests wait for one fetch. Null when unreachable. */
    override suspend fun token(): String? = current.value ?: fetching.withLock { current.value ?: fetch() }

    override fun tokenWasRejected() {
        current.value = null
    }

    /**
     * A new token, the one held dropped. The platform reads a dev token's `auth_time` as the moment it was minted, so
     * this is the local stand-in for signing in again (a change of contact asks for a recent sign-in).
     */
    suspend fun renew() {
        fetching.withLock {
            current.value = null
            fetch()
        }
    }

    private suspend fun fetch(): String? = try {
        contract.call(ContractOperations.DEV_TOKEN, DevTokenRequestDto(userId = subject, client = client)).token
            .also { current.value = it }
    } catch (@Suppress("SwallowedException") unreachable: AppErrorException) {
        // The local platform is not running, or refused: the request goes without a token and the server says why.
        // Unless the caller itself is gone — the stack reports a cancelled call as Retryable (ADR-0026).
        currentCoroutineContext().ensureActive()
        null
    }
}
