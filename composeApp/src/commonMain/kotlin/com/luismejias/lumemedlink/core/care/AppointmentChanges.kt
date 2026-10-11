package com.luismejias.lumemedlink.core.care

import com.luismejias.lumemedlink.shared.CalendarDate

/**
 * Why the patient moves or cancels — the contract's own list (`CANNOT_ATTEND`, `SCHEDULE_CONFLICT`, `OTHER`), never a
 * health reason (ADR-0001: the design's «Ya me siento mejor» is left out by the author's call of 2026-10-11).
 */
internal enum class ChangeReason { CANNOT_ATTEND, SCHEDULE_CONFLICT, OTHER }

/** Why a change did not go through. */
internal sealed interface ChangeFailure {
    /** The new hour was taken between reading and moving — the server re-verifies it (409, e10). */
    data object SlotTaken : ChangeFailure

    /** Inside the doctor's change window: the server refuses with `cancellation_window_closed` (409). */
    data object WindowClosed : ChangeFailure

    data object Offline : ChangeFailure

    data object Unavailable : ChangeFailure
}

internal sealed interface ChangeResult {
    /** A move answers with the new appointment; while the doctor reviews it, the original keeps its hour (spec f3). */
    data class Moved(val appointment: PatientAppointment) : ChangeResult

    /** A cancel or a withdrawal took effect. */
    data object Done : ChangeResult

    data class Failed(val failure: ChangeFailure) : ChangeResult
}

/** Why a day of the doctor's calendar has no hours (f3): the contract's closed periods, as the design names them. */
internal enum class ClosedKind { HOLIDAY, NO_ATTENTION }

internal data class ClosedDay(val date: CalendarDate, val kind: ClosedKind)
