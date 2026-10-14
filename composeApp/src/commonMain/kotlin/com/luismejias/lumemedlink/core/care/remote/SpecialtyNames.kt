package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.networking.AppErrorException
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.contract.ContractOperations
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The platform's specialty catalog (`listSpecialties`), read once and kept in memory: a code on an appointment or a
 * doctor becomes its name, in the platform's words (ADR-0063). Shown after sign-in only.
 */
internal class SpecialtyNames(private val contract: ContractClient) {
    private val names = MutableStateFlow<Map<String, String>?>(null)
    private val loading = Mutex()

    /**
     * Every code's name. A catalog that cannot be read gives none, and is asked for again next time: an appointment
     * without its specialty's name is still an appointment, and it must not fail because of it.
     */
    suspend fun all(): Map<String, String> = names.value ?: loading.withLock { names.value ?: load() }

    private suspend fun load(): Map<String, String> = try {
        contract.call(ContractOperations.LIST_SPECIALTIES).associate { it.code to it.name }.also { names.value = it }
    } catch (@Suppress("SwallowedException") unreadable: AppErrorException) {
        currentCoroutineContext().ensureActive()
        emptyMap()
    }
}
