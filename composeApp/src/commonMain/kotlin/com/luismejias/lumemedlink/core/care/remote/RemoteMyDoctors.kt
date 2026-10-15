package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.AppointmentsSource
import com.luismejias.lumemedlink.core.care.CareDoctor
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.MyDoctor
import com.luismejias.lumemedlink.core.care.MyDoctorsSource
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.contract.ContractOperations
import com.luismejias.lumemedlink.core.networking.contract.SharedProfessionalContactListDtoItemsItem

/**
 * «Mis médicos» over the contract (task `0037`): `listMyProfessionalContacts`, the professionals who attended the
 * patient, with the channels each one chooses to share.
 *
 * Two things the screen needs and the route does not say, derived here:
 * - **One entry per professional.** The route lists one per clinic, so a doctor who saw the patient at two clinics
 *   comes twice; the screen keys its list by the doctor. The channels are the first each clinic's entry shares.
 * - **How many attentions.** The completed appointments with that professional, from the patient's own list. The
 *   design shows the count; the contract has none to give.
 */
internal class RemoteMyDoctors(
    private val contract: ContractClient,
    private val specialties: SpecialtyNames,
    private val appointments: AppointmentsSource,
) : MyDoctorsSource {
    override suspend fun myDoctors(): CareResult<List<MyDoctor>> {
        val attended = when (val read = appointments.appointments()) {
            is CareResult.Failed -> return read
            is CareResult.Ok -> read.value.filter { it.status == AppointmentStatus.COMPLETED }
                .groupingBy { it.doctor.id }
                .eachCount()
        }
        return careRead {
            val names = specialties.all()
            contract.call(ContractOperations.LIST_MY_PROFESSIONAL_CONTACTS).items
                .groupBy { it.professionalId }
                .map { (id, entries) -> entries.toMyDoctor(id, attended[id] ?: 0, names) }
        }
    }
}

private fun List<SharedProfessionalContactListDtoItemsItem>.toMyDoctor(
    id: String,
    attentions: Int,
    specialties: Map<String, String>,
): MyDoctor {
    val first = first()
    return MyDoctor(
        doctor = CareDoctor(
            id = id,
            displayName = professionalDisplayName(first.givenName, first.familyName),
            specialty = firstNotNullOfOrNull { it.specialtyCode }?.let(specialties::get).orEmpty(),
        ),
        attentions = attentions,
        phone = firstNotNullOfOrNull { it.phone },
        email = firstNotNullOfOrNull { it.email },
    )
}
