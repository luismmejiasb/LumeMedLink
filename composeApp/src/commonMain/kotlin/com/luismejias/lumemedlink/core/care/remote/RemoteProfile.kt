package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.CareFailure
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.PatientProfile
import com.luismejias.lumemedlink.core.care.PatientProfileSource
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.contract.ContractOperations
import com.luismejias.lumemedlink.core.networking.contract.SelfProfileDto
import com.luismejias.lumemedlink.shared.ChileanRut
import com.luismejias.lumemedlink.shared.EmailAddress
import com.luismejias.lumemedlink.shared.PhoneNumber

/**
 * The patient's profile over the contract (task `0036`): `listMyPatientProfiles`, one profile per clinic, shown as one
 * (the author, 2026-10-14: «uno solo, cambio en todas»).
 *
 * Which one is a rule, not the server's order: the clinic first by name, then by id, so the same account always shows
 * the same profile. Until a contact change lands in every clinic at once (request `0012`), two clinics may hold
 * different contacts, and the one shown is that clinic's.
 *
 * Read only. A contact change is applied after its code is confirmed (request `0012`); `updateMyPatientProfile` applies
 * it at once, so it is never called for a contact, and the address is not edited in this app.
 */
internal class RemoteProfile(private val contract: ContractClient) : PatientProfileSource {
    override suspend fun profile(): CareResult<PatientProfile> =
        when (val profiles = careRead { contract.call(ContractOperations.LIST_MY_PATIENT_PROFILES) }) {
            is CareResult.Failed -> profiles
            // An account always comes from a clinic's code (ADR-0058), so none is the platform answering wrong.
            is CareResult.Ok -> profiles.value.minWithOrNull(compareBy({ it.tenantName }, { it.tenantId }))
                ?.let { CareResult.Ok(it.toPatientProfile()) }
                ?: CareResult.Failed(CareFailure.Unavailable)
        }
}

/** One clinic's profile as p7 draws it. A contact the clinic does not hold, or holds in no shape, is not recorded. */
internal fun SelfProfileDto.toPatientProfile(): PatientProfile = PatientProfile(
    givenNames = givenName,
    familyNames = familyName,
    rut = ChileanRut.parse(rut),
    mobile = phone?.let(PhoneNumber::stored),
    email = email?.let(EmailAddress::parse),
    verified = false,
)
