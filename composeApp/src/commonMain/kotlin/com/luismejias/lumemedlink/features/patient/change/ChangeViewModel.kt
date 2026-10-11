package com.luismejias.lumemedlink.features.patient.change

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.DirectoryGateway
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.PatientCareGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The sheets over a tab (f1, f2). */
internal sealed interface ChangeSheet {
    data class Directions(val appointment: PatientAppointment) : ChangeSheet

    data class Options(val options: ChangeOptions) : ChangeSheet
}

/** The screens pushed over a tab when the patient changes an appointment (f3, f4). */
internal enum class ChangeRoute { RESCHEDULE, CANCEL }

/** What finished, for the tab host to refresh what it shows and to say so in a toast. */
internal sealed interface ChangeDone {
    data class Moved(val appointment: PatientAppointment) : ChangeDone

    data object Cancelled : ChangeDone
}

/**
 * «Tu próxima cita» (f1–f4): reached from Inicio's card and from Mis citas, so the tab host owns one (ADR-0045: a
 * flow's state lives in its own owner, not in a screen).
 */
internal class ChangeViewModel(private val care: PatientCareGateway, directory: DirectoryGateway) : ViewModel() {
    private val shownSheet = MutableStateFlow<ChangeSheet?>(null)
    val sheet: StateFlow<ChangeSheet?> = shownSheet.asStateFlow()

    private val shownRoute = MutableStateFlow<ChangeRoute?>(null)
    val route: StateFlow<ChangeRoute?> = shownRoute.asStateFlow()

    private val finished = MutableStateFlow<ChangeDone?>(null)
    val done: StateFlow<ChangeDone?> = finished.asStateFlow()

    val reschedule = RescheduleModel(care, directory)
    val cancel = CancelModel(care)

    /** Runs a step in this ViewModel's scope — a screen never owns the work it starts. */
    fun launch(step: suspend ChangeViewModel.() -> Unit) {
        viewModelScope.launch { step() }
    }

    fun openDirections(appointment: PatientAppointment) {
        shownSheet.value = ChangeSheet.Directions(appointment)
    }

    /** f2. The practice's phone is read only to offer it once the window has closed. */
    suspend fun openOptions(appointment: PatientAppointment) {
        val phone = (care.myDoctors() as? CareResult.Ok)?.value
            ?.firstOrNull { it.doctor.id == appointment.doctor.id }?.phone
        shownSheet.value = ChangeSheet.Options(changeOptionsFor(appointment, care.today(), care.now(), phone))
    }

    fun dismissSheet() {
        shownSheet.value = null
    }

    /** f2 → f3. Mis citas' «Cambiar» comes here directly. */
    suspend fun changeTime(appointment: PatientAppointment) {
        shownSheet.value = null
        shownRoute.value = ChangeRoute.RESCHEDULE
        reschedule.load(appointment)
    }

    /** f2 → f4. Mis citas' «Cancelar» comes here directly. */
    fun cancelAppointment(appointment: PatientAppointment) {
        shownSheet.value = null
        cancel.start(appointment)
        shownRoute.value = ChangeRoute.CANCEL
    }

    fun back() {
        shownRoute.value = null
        cancel.clear()
    }

    fun moved(appointment: PatientAppointment) {
        shownRoute.value = null
        finished.value = ChangeDone.Moved(appointment)
    }

    fun cancelled() {
        shownRoute.value = null
        cancel.clear()
        finished.value = ChangeDone.Cancelled
    }

    fun doneSeen() {
        finished.value = null
    }

    /** f4's warning names the change as the better road; it opens f3 for the same appointment. */
    suspend fun changeInstead() {
        val appointment = cancel.state.value?.appointment ?: return
        cancel.clear()
        if (appointment.status in setOf(AppointmentStatus.CONFIRMED, AppointmentStatus.REQUESTED)) {
            changeTime(appointment)
        }
    }
}
