package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.AppointmentChanges
import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.AppointmentsSource
import com.luismejias.lumemedlink.core.care.CareDoctor
import com.luismejias.lumemedlink.core.care.CarePlace
import com.luismejias.lumemedlink.core.care.CarePolicy
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.ChangeFailure
import com.luismejias.lumemedlink.core.care.ChangeReason
import com.luismejias.lumemedlink.core.care.ChangeResult
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.Slot
import com.luismejias.lumemedlink.core.networking.ContractClient
import com.luismejias.lumemedlink.core.networking.contract.CancelMyAppointmentDto
import com.luismejias.lumemedlink.core.networking.contract.CancelMyAppointmentDtoCancellationReason
import com.luismejias.lumemedlink.core.networking.contract.ContractOperations
import com.luismejias.lumemedlink.core.networking.contract.PatientAppointmentDto
import com.luismejias.lumemedlink.core.networking.contract.RescheduleMyAppointmentDto
import com.luismejias.lumemedlink.core.networking.contract.RescheduleMyAppointmentDtoRescheduleReason
import com.luismejias.lumemedlink.core.networking.contract.WithdrawMyAppointmentRequestDto
import com.luismejias.lumemedlink.core.networking.contract.WithdrawMyAppointmentRequestDtoCancellationReason
import kotlinx.datetime.TimeZone

/** How far ahead a change is allowed when the design asks for one number: each appointment carries its own. */
private const val DESIGN_CHANGE_WINDOW_HOURS = 24

/** The platform's reminders, 24 h and 2 h before (its task `0121`, an amendment of its `ADR-0034`). */
private val PLATFORM_REMINDERS_HOURS = listOf(24, 2)

private const val MINUTES_PER_HOUR = 60

/**
 * The patient's appointments over the contract (task `0036`): `listMyAppointments`, `cancelMyAppointment`,
 * `withdrawMyAppointmentRequest` and `rescheduleMyAppointment`, never composed from two writes (T7). Every hour is
 * shown on its place's wall clock ([zones], ADR-0063), and the specialty's name comes from the platform's catalog.
 */
internal class RemoteAppointments(
    private val contract: ContractClient,
    private val specialties: SpecialtyNames,
    private val zones: PlaceZones = PlaceZones(),
) : AppointmentsSource,
    AppointmentChanges {
    override suspend fun appointments(): CareResult<List<PatientAppointment>> = careRead {
        val names = specialties.all()
        contract.call(ContractOperations.LIST_MY_APPOINTMENTS)
            .onEach { zones.learn(it.locationId, it.timeZone) }
            .map { it.toPatientAppointment(zones.fallback, names) }
            .sortedWith(compareBy({ it.date }, { it.start }))
    }

    /**
     * The design's one window («hasta 24 h antes») and the platform's reminders. Not a read: the contract carries the
     * window on each appointment (`cancelCutoffHours`), which is what a change is judged by.
     */
    override suspend fun policy(): CareResult<CarePolicy> =
        CareResult.Ok(CarePolicy(DESIGN_CHANGE_WINDOW_HOURS, PLATFORM_REMINDERS_HOURS))

    override suspend fun cancel(appointment: PatientAppointment, reason: ChangeReason): ChangeResult = careChange {
        contract.call(
            ContractOperations.CANCEL_MY_APPOINTMENT,
            CancelMyAppointmentDto(CancelMyAppointmentDtoCancellationReason(reason.wire())),
            path = mapOf("id" to appointment.id),
        )
        ChangeResult.Done
    }

    /** The contract asks a reason of a withdrawal too; the design asks none, so it sends the neutral one. */
    override suspend fun withdraw(appointment: PatientAppointment): ChangeResult = careChange {
        contract.call(
            ContractOperations.WITHDRAW_MY_APPOINTMENT_REQUEST,
            WithdrawMyAppointmentRequestDto(WithdrawMyAppointmentRequestDtoCancellationReason.OTHER),
            path = mapOf("id" to appointment.id),
        )
        ChangeResult.Done
    }

    /**
     * One atomic operation, never a cancel plus a booking (T7). The appointment must say its place (`locationId`,
     * since 0.130.0): a home visit or a call has none, cannot be moved to a place's hour, and says so without calling.
     */
    override suspend fun reschedule(
        appointment: PatientAppointment,
        slot: Slot,
        reason: ChangeReason,
        clientId: String,
    ): ChangeResult {
        if (appointment.locationId.isBlank()) return ChangeResult.Failed(ChangeFailure.Unavailable)
        return careChange {
            // Back to UTC on the clock the hour was shown on: the place's.
            val starts = instantOf(slot.date, slot.start, zones.of(appointment.locationId))
            val ends = instantPlusMinutes(starts, slot.minutes)
            val moved = contract.call(
                ContractOperations.RESCHEDULE_MY_APPOINTMENT,
                RescheduleMyAppointmentDto(
                    locationId = appointment.locationId,
                    startsAt = starts,
                    endsAt = ends,
                    rescheduleReason = RescheduleMyAppointmentDtoRescheduleReason(reason.wire()),
                    clientId = clientId,
                ),
                path = mapOf("id" to appointment.id),
            )
            zones.learn(moved.locationId, moved.timeZone)
            ChangeResult.Moved(moved.toPatientAppointment(zones.fallback, specialties.all()))
        }
    }
}

/** The contract's reason catalog has the design's three words exactly. */
private fun ChangeReason.wire(): String = name

/**
 * One appointment as the design draws it. Never its clinical reason: the patient's form has none (§1.0). The status's
 * open vocabulary folds into the design's: `SCHEDULED` and `CONFIRMED` both read «Confirmada», and a word the app does
 * not know is [AppointmentStatus.OTHER], shown without a verdict.
 */
internal fun PatientAppointmentDto.toPatientAppointment(
    fallbackZone: TimeZone,
    specialties: Map<String, String>,
): PatientAppointment {
    // The place's own zone, since 0.130.0; the fallback only when the contract names none (ADR-0063).
    val wall = wallTimeOf(startsAt, zoneOf(timeZone, fallbackZone))
    return PatientAppointment(
        id = id,
        doctor = CareDoctor(
            id = professionalId.orEmpty(),
            displayName = professionalName ?: clinicName,
            specialty = specialtyCode?.let(specialties::get).orEmpty(),
        ),
        date = wall.date,
        start = wall.time,
        minutes = minutesBetween(startsAt, endsAt),
        status = appointmentStatus(status),
        place = CarePlace(
            address = locationAddress?.let { listOfNotNull(it.street, it.streetNumber).joinToString(" ") }.orEmpty(),
            detail = listOfNotNull(locationLabel, clinicName).distinct().joinToString(" · "),
        ),
        // Null for a home visit or a call: such an hour cannot be moved to another of a place's hours.
        locationId = locationId.orEmpty(),
        // A cutoff the contract cannot state (the professional's profile is gone) cancels as «until the start».
        changeCutoffMinutes = (cancelCutoffHours ?: 0) * MINUTES_PER_HOUR,
    )
}

/** The contract's status, folded into the design's. */
internal fun appointmentStatus(wire: String): AppointmentStatus = when (wire) {
    "REQUESTED" -> AppointmentStatus.REQUESTED
    "SCHEDULED", "CONFIRMED" -> AppointmentStatus.CONFIRMED
    "COMPLETED" -> AppointmentStatus.COMPLETED
    "CANCELLED" -> AppointmentStatus.CANCELLED
    else -> AppointmentStatus.OTHER
}
