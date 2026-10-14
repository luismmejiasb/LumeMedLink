package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.contract.ContractOperations

/**
 * The platform's specialty catalog (`listSpecialties`), read once: a code on an appointment or a doctor becomes its
 * name, in the platform's words (ADR-0063). Shown after sign-in only. A catalog that cannot be read gives no names,
 * and an appointment without its specialty's name is still an appointment.
 */
internal class SpecialtyNames(contract: ContractClient) {
    private val catalog = ReadOnce(emptyMap()) {
        contract.call(ContractOperations.LIST_SPECIALTIES).associate { it.code to it.name }
    }

    suspend fun all(): Map<String, String> = catalog.get()
}
