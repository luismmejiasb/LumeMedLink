package com.luismejias.lumemedlink.features.patient.change

import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.ChangeFailure
import com.luismejias.lumemedlink.core.care.ChangeReason
import com.luismejias.lumemedlink.core.care.ChangeResult
import com.luismejias.lumemedlink.core.care.ClosedDay
import com.luismejias.lumemedlink.core.care.ClosedKind
import com.luismejias.lumemedlink.core.care.DirectoryGateway
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.PatientCareGateway
import com.luismejias.lumemedlink.core.care.SentRating
import com.luismejias.lumemedlink.core.care.Slot
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.core.screen.StandIn
import com.luismejias.lumemedlink.core.screen.allOf
import com.luismejias.lumemedlink.core.screen.ok
import com.luismejias.lumemedlink.core.telemetry.AnalyticsEvent
import com.luismejias.lumemedlink.features.patient.parts.upcoming
import com.luismejias.lumemedlink.shared.CalendarDate
import com.luismejias.lumemedlink.shared.ClockTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Minutes from now until [this] begins, on the clinic's wall clock. Negative once it has begun. */
internal fun PatientAppointment.minutesFrom(today: CalendarDate, now: ClockTime): Int =
    today.daysUntil(date) * PatientAppointment.DAY_MINUTES + start.minuteOfDay - now.minuteOfDay

private val OPEN = setOf(AppointmentStatus.CONFIRMED, AppointmentStatus.REQUESTED)

/**
 * f2: what the patient may still do with an appointment. Inside the doctor's window both choices are drawn and refused
 * — never hidden — and the practice's phone is offered when the doctor shares it (spec f2). A request the practice has
 * not answered can be withdrawn at any time.
 */
internal data class ChangeOptions(
    val appointment: PatientAppointment,
    val canChange: Boolean,
    val canCancel: Boolean,
    /** Offered only once the window has closed, and only when the doctor shares it. */
    val practicePhone: String?,
) {
    val cutoffMinutes: Int get() = appointment.changeCutoffMinutes

    override fun toString(): String = "ChangeOptions(change=$canChange, cancel=$canCancel)"
}

internal fun changeOptionsFor(
    appointment: PatientAppointment,
    today: CalendarDate,
    now: ClockTime,
    sharedPhone: String?,
): ChangeOptions {
    val inTime = appointment.minutesFrom(today, now) >= appointment.changeCutoffMinutes
    val open = appointment.status in OPEN
    return ChangeOptions(
        appointment = appointment,
        canChange = open && inTime,
        canCancel = appointment.status == AppointmentStatus.REQUESTED || (open && inTime),
        practicePhone = sharedPhone.takeIf { !inTime },
    )
}

// ── f3 · Cambiar hora, and e10 ──────────────────────────────────────────────────────────────────

private val NOON = ClockTime(12, 0)

/** One day of f3's strip: how many hours are still free, or why there are none. */
internal data class DayInfo(val date: CalendarDate, val free: Int, val closed: ClosedKind?) {
    val open: Boolean get() = free > 0
}

/** One hour of f3's grid: free to choose, or taken while the patient was choosing (e10: struck through). */
internal sealed interface MoveTile {
    val slot: Slot

    data class Free(override val slot: Slot) : MoveTile

    data class Taken(override val slot: Slot) : MoveTile
}

/** What the move answered. */
internal sealed interface MoveOutcome {
    data class Moved(val appointment: PatientAppointment) : MoveOutcome

    /** e10: the hour went to somebody else; it stays struck through and the patient chooses again. */
    data class Taken(val slot: Slot) : MoveOutcome

    data class Refused(val failure: ChangeFailure) : MoveOutcome
}

internal data class RescheduleContent(
    val appointment: PatientAppointment,
    /** The free hours in the window, minus any on another appointment the patient holds. */
    val slots: List<Slot>,
    /** Hours that were free when read and taken when the patient tried them (e10). */
    val taken: Set<Slot>,
    val closed: List<ClosedDay>,
    val firstDay: CalendarDate,
    val lastDay: CalendarDate,
    val day: CalendarDate?,
    val slot: Slot?,
    val outcome: MoveOutcome? = null,
) {
    val days: List<DayInfo>
        get() = (0..firstDay.daysUntil(lastDay)).map { offset ->
            val date = firstDay.plusDays(offset)
            DayInfo(date, freeOn(date).size, closed.firstOrNull { it.date == date }?.kind)
        }

    val morning: List<MoveTile> get() = tiles().filter { it.slot.start < NOON }

    val afternoon: List<MoveTile> get() = tiles().filter { it.slot.start >= NOON }

    /** The day shown, once it has run out of hours: e10's «El martes 22 hay 6». */
    val nextOpen: DayInfo? get() = day?.let { shown -> days.firstOrNull { it.date > shown && it.open } }

    val freeToday: Int get() = day?.let { freeOn(it).size } ?: 0

    private fun freeOn(date: CalendarDate): List<Slot> = slots.filter { it.date == date && it !in taken }

    private fun tiles(): List<MoveTile> = slots.filter { it.date == day }.map {
        if (it in taken) MoveTile.Taken(it) else MoveTile.Free(it)
    }

    override fun toString(): String = "RescheduleContent(slots=${slots.size}, chosen=${slot != null})"
}

@OptIn(ExperimentalUuidApi::class)
internal class RescheduleModel(
    private val care: PatientCareGateway,
    private val directory: DirectoryGateway,
    private val logEvent: (AnalyticsEvent) -> Unit = {},
    private val standInModel: (() -> RescheduleModel)? = null,
) {
    private val current = MutableStateFlow<CareLoad<RescheduleContent>>(CareLoad.Loading)
    val state: StateFlow<CareLoad<RescheduleContent>> = current.asStateFlow()

    /** The screen's own shape while it waits (ADR-0055), read by [standInModel]: this model, against the script. */
    private val standInContent = StandIn<RescheduleContent>()
    val standIn: StateFlow<RescheduleContent?> = standInContent.content

    /** This move's key (the contract's required `clientId`): new for a new hour, the same for every retry of one. */
    private var clientId: String = Uuid.random().toString()

    /** Reads the doctor's hours from today to the horizon, opening on the first day that has one. */
    suspend fun load(appointment: PatientAppointment) {
        standInContent.fill {
            standInModel?.invoke()?.let {
                it.load(appointment)
                it.state.value
            } ?: CareLoad.Loading
        }
        current.value = CareLoad.Loading
        val from = care.today()
        val to = from.plusDays(HORIZON_DAYS)
        val doctor = appointment.doctor.id
        val free = directory.availability(doctor, appointment.locationId, from, to)
        val closed = directory.closedDays(doctor, appointment.locationId, from, to)
        val own = care.appointments()
        current.value = allOf(free, closed, own) {
            val mine = own.ok.upcoming(from)
            val slots = free.ok.filterNot { slot -> mine.any { it.date == slot.date && it.start == slot.start } }
            val day = slots.firstOrNull()?.date
            RescheduleContent(appointment, slots, emptySet(), closed.ok, from, to, day, null)
        }
    }

    fun dayChosen(day: CalendarDate) = update { copy(day = day, slot = null) }

    fun slotChosen(slot: Slot?) {
        val ready = (current.value as? CareLoad.Ready)?.content ?: return
        if (slot != ready.slot) clientId = Uuid.random().toString()
        update { copy(slot = slot, outcome = null) }
    }

    /** «Cambiar a lun 21 · 10:30»: ONE request to the contract's atomic move (T7). */
    suspend fun submit() {
        val ready = (current.value as? CareLoad.Ready)?.content ?: return
        val slot = ready.slot ?: return
        val result = care.reschedule(ready.appointment, slot, RESCHEDULE_REASON, clientId)
        if (result is ChangeResult.Moved) logEvent(AnalyticsEvent.APPOINTMENT_RESCHEDULED)
        update {
            when (result) {
                is ChangeResult.Moved -> copy(outcome = MoveOutcome.Moved(result.appointment))
                ChangeResult.Done -> this
                is ChangeResult.Failed -> if (result.failure == ChangeFailure.SlotTaken) {
                    copy(taken = taken + slot, slot = null, outcome = MoveOutcome.Taken(slot))
                } else {
                    copy(outcome = MoveOutcome.Refused(result.failure))
                }
            }
        }
    }

    fun outcomeSeen() = update { copy(outcome = null) }

    private fun update(change: RescheduleContent.() -> RescheduleContent) {
        val ready = current.value as? CareLoad.Ready ?: return
        current.value = CareLoad.Ready(ready.content.change())
    }

    private companion object {
        const val HORIZON_DAYS = 30

        /** f3 asks for no reason and the contract requires one: a move the patient chose is theirs, so `OTHER`. */
        val RESCHEDULE_REASON = ChangeReason.OTHER
    }
}

// ── f4 · Cancelar cita ──────────────────────────────────────────────────────────────────────────

/**
 * f4's reasons, each one of the contract's. «Ya me siento mejor» is not here: it is health information (ADR-0001,
 * the author's call of 2026-10-11).
 */
internal enum class CancelChoice(val reason: ChangeReason) {
    NOT_AT_THAT_TIME(ChangeReason.SCHEDULE_CONFLICT),
    ANOTHER_DOCTOR(ChangeReason.CANNOT_ATTEND),
    OTHER(ChangeReason.OTHER),
}

internal sealed interface CancelOutcome {
    data object Cancelled : CancelOutcome

    data class Refused(val failure: ChangeFailure) : CancelOutcome
}

internal data class CancelState(
    val appointment: PatientAppointment,
    /** Optional (spec f4): none chosen is sent as `OTHER`, which the contract requires. */
    val choice: CancelChoice? = null,
    val outcome: CancelOutcome? = null,
) {
    override fun toString(): String = "CancelState(choice=$choice, outcome=${outcome?.let { it::class.simpleName }})"
}

internal class CancelModel(private val care: PatientCareGateway, private val logEvent: (AnalyticsEvent) -> Unit = {}) {
    private val current = MutableStateFlow<CancelState?>(null)
    val state: StateFlow<CancelState?> = current.asStateFlow()

    fun start(appointment: PatientAppointment) {
        current.value = CancelState(appointment)
    }

    fun choiceMade(choice: CancelChoice?) {
        current.value = current.value?.copy(choice = choice)
    }

    /**
     * Cancels a confirmed appointment, or withdraws a request — the contract's two verbs. Never queued: offline it is
     * refused now, and the patient tries again (spec f4).
     */
    suspend fun submit() {
        val state = current.value ?: return
        val result = if (state.appointment.status == AppointmentStatus.REQUESTED) {
            care.withdraw(state.appointment)
        } else {
            care.cancel(state.appointment, state.choice?.reason ?: ChangeReason.OTHER)
        }
        if (result !is ChangeResult.Failed) logEvent(AnalyticsEvent.APPOINTMENT_CANCELLED)
        current.value = state.copy(
            outcome = when (result) {
                is ChangeResult.Failed -> CancelOutcome.Refused(result.failure)
                else -> CancelOutcome.Cancelled
            },
        )
    }

    fun outcomeSeen() {
        current.value = current.value?.copy(outcome = null)
    }

    fun clear() {
        current.value = null
    }
}

// ── c1–c3 · Detalle de una cita ─────────────────────────────────────────────────────────────────

internal data class DetailContent(
    val appointment: PatientAppointment,
    /** What the bottom bar may still do, with the practice's phone once the window has closed (spec c1). */
    val options: ChangeOptions,
    /** «Contacto de la consulta»: only when the doctor shares it (spec c1). */
    val phone: String?,
    /** c3: the rating already sent, or null to offer the stars. */
    val rating: SentRating?,
) {
    override fun toString(): String = "DetailContent(status=${appointment.status})"
}

internal class DetailModel(
    private val care: PatientCareGateway,
    private val standInModel: (() -> DetailModel)? = null,
) {
    private val current = MutableStateFlow<CareLoad<DetailContent>>(CareLoad.Loading)
    val state: StateFlow<CareLoad<DetailContent>> = current.asStateFlow()

    /** The screen's own shape while it waits (ADR-0055), read by [standInModel]: this model, against the script. */
    private val standInContent = StandIn<DetailContent>()
    val standIn: StateFlow<DetailContent?> = standInContent.content

    /** The appointment the screen asked for last: an older load that finishes later must not overwrite it. */
    private var requested: String? = null

    suspend fun load(appointment: PatientAppointment) {
        standInContent.fill {
            standInModel?.invoke()?.let {
                it.load(appointment)
                it.state.value
            } ?: CareLoad.Loading
        }
        requested = appointment.id
        val shown = (current.value as? CareLoad.Ready)?.content?.appointment?.id
        // Reading the same appointment again (after f5) keeps it on screen; another one starts from the spinner.
        if (shown != appointment.id) current.value = CareLoad.Loading
        val doctors = care.myDoctors()
        val rating = if (appointment.status == AppointmentStatus.COMPLETED) {
            care.ratingFor(appointment.id)
        } else {
            CareResult.Ok(null)
        }
        if (requested != appointment.id) return
        val loaded = allOf(doctors, rating) {
            val phone = doctors.ok.firstOrNull { it.doctor.id == appointment.doctor.id }?.phone
            DetailContent(
                appointment = appointment,
                options = changeOptionsFor(appointment, care.today(), care.now(), phone),
                phone = phone,
                rating = rating.ok,
            )
        }
        // A failed re-read of what is already shown keeps it: the rating went through, only the refresh did not.
        if (loaded is CareLoad.Failed && shown == appointment.id) return
        current.value = loaded
    }
}
