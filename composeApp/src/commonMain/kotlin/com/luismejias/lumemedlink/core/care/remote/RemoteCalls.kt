package com.luismejias.lumemedlink.core.care.remote

import com.luismejias.lumemedlink.core.care.CareFailure
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.ChangeFailure
import com.luismejias.lumemedlink.core.care.ChangeResult
import com.luismejias.lumemedlink.core.networking.AppErrorException
import com.luismejias.lumemedlink.shared.AppError
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * The contract's problem code (`/problems/cancellation_window_closed` → `cancellation_window_closed`), or null. The
 * code is the contract's vocabulary, safe to branch on; never shown (ADR-0004).
 */
internal fun AppError.problemCode(): String? = when (this) {
    is AppError.Validation -> problemType
    is AppError.Unexpected -> problemType
    else -> null
}?.substringAfterLast('/')

/** What a failed read says to the design: no network, or something the app cannot use (never a code). */
internal fun AppError.careFailure(): CareFailure =
    if (this is AppError.Retryable && status == null) CareFailure.Offline else CareFailure.Unavailable

/**
 * Runs a read and folds the stack's error into [CareResult]. A cancelled caller is not a failure: the stack reports a
 * cancelled call as `Retryable`, so the caller's own job decides (ADR-0026).
 */
internal suspend fun <T> careRead(read: suspend () -> T): CareResult<T> = try {
    CareResult.Ok(read())
} catch (refused: AppErrorException) {
    currentCoroutineContext().ensureActive()
    CareResult.Failed(refused.error.careFailure())
}

/** Runs a change and folds the stack's error into [ChangeResult], by the contract's problem codes. */
internal suspend fun careChange(change: suspend () -> ChangeResult): ChangeResult = try {
    change()
} catch (refused: AppErrorException) {
    currentCoroutineContext().ensureActive()
    ChangeResult.Failed(refused.error.changeFailure())
}

/** The contract's 409s, as the design names them; anything else is no network or «unavailable». */
internal fun AppError.changeFailure(): ChangeFailure = when (problemCode()) {
    "cancellation_window_closed" -> ChangeFailure.WindowClosed
    // The hour was taken, closed or clashes with another of the patient's between reading and writing: the server
    // re-verifies it (409), and the design says the same thing for the three.
    "conflict", "booking_closed", "patient_unavailable" -> ChangeFailure.SlotTaken
    else -> if (this is AppError.Retryable && status == null) ChangeFailure.Offline else ChangeFailure.Unavailable
}
