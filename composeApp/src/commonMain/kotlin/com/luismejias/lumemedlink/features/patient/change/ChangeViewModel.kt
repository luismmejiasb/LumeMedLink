package com.luismejias.lumemedlink.features.patient.change

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.AppointmentChanges
import com.luismejias.lumemedlink.core.care.AppointmentStatus
import com.luismejias.lumemedlink.core.care.AppointmentsSource
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.ClinicClock
import com.luismejias.lumemedlink.core.care.DirectoryAvailability
import com.luismejias.lumemedlink.core.care.DirectoryDoctor
import com.luismejias.lumemedlink.core.care.DirectoryGateway
import com.luismejias.lumemedlink.core.care.DirectorySearch
import com.luismejias.lumemedlink.core.care.MyDoctorsSource
import com.luismejias.lumemedlink.core.care.PatientAppointment
import com.luismejias.lumemedlink.core.care.PatientCareGateway
import com.luismejias.lumemedlink.core.care.RatingsGateway
import com.luismejias.lumemedlink.core.care.standInPatientCare
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.core.telemetry.AnalyticsEvent
import com.luismejias.lumemedlink.shared.OpaqueRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The sheets over a tab (f1, f2). */
internal sealed interface ChangeSheet {
    data class Directions(val appointment: PatientAppointment) : ChangeSheet

    data class Options(val options: ChangeOptions) : ChangeSheet
}

/** The screens pushed over a tab for one appointment: its detail (c1–c3), and changing or cancelling it (f3, f4). */
internal enum class ChangeRoute { DETAIL, RESCHEDULE, CANCEL }

/** What finished, for the tab host to refresh what it shows and to say so in a toast. */
internal sealed interface ChangeDone {
    data class Moved(val appointment: PatientAppointment) : ChangeDone

    data object Cancelled : ChangeDone
}

/** What an appointment's flow reads and writes, each capability on its own (interface segregation, task 0036). */
internal class ChangeSources(
    val clock: ClinicClock,
    val appointments: AppointmentsSource,
    val changes: AppointmentChanges,
    val doctors: MyDoctorsSource,
    val ratings: RatingsGateway,
    val availability: DirectoryAvailability,
    val directory: DirectorySearch,
)

/** Every source of the flow from what implements all of them: the script, for the stand-in and the tests. */
internal fun changeSourcesOf(care: PatientCareGateway, directory: DirectoryGateway): ChangeSources =
    ChangeSources(care, care, care, care, care, directory, directory)

/**
 * One appointment's flow — its detail (c1–c3) and «Tu próxima cita» (f1–f4) — reached from Inicio and from Mis citas,
 * so the tab host owns one (ADR-0045: a flow's state lives in its own owner, not in a screen).
 */
internal class ChangeViewModel(private val sources: ChangeSources, logEvent: (AnalyticsEvent) -> Unit = {}) :
    ViewModel() {
    private val shownSheet = MutableStateFlow<ChangeSheet?>(null)
    val sheet: StateFlow<ChangeSheet?> = shownSheet.asStateFlow()

    /** The detail may sit under a change, and «Volver» returns to it: a stack, not a single screen. */
    private val stack = MutableStateFlow<List<ChangeRoute>>(emptyList())
    val routes: StateFlow<List<ChangeRoute>> = stack.asStateFlow()

    private val finished = MutableStateFlow<ChangeDone?>(null)
    val done: StateFlow<ChangeDone?> = finished.asStateFlow()

    val reschedule = with(sources) {
        RescheduleModel(clock, appointments, changes, availability, logEvent) {
            standInPatientCare().let { stand -> RescheduleModel(stand, stand, stand, stand) }
        }
    }
    val cancel = CancelModel(sources.changes, logEvent)
    val detail = DetailModel(sources.clock, sources.doctors, sources.ratings) {
        standInPatientCare().let { stand -> DetailModel(stand, stand, stand) }
    }

    /** Runs a step in this ViewModel's scope — a screen never owns the work it starts. */
    fun launch(step: suspend ChangeViewModel.() -> Unit) {
        viewModelScope.launch { step() }
    }

    fun openDirections(appointment: PatientAppointment) {
        shownSheet.value = ChangeSheet.Directions(appointment)
    }

    /** f2. The practice's phone is read only to offer it once the window has closed. */
    suspend fun openOptions(appointment: PatientAppointment) {
        val phone = (sources.doctors.myDoctors() as? CareResult.Ok)?.value
            ?.firstOrNull { it.doctor.id == appointment.doctor.id }?.phone
        val clock = sources.clock
        shownSheet.value = ChangeSheet.Options(changeOptionsFor(appointment, clock.today(), clock.now(), phone))
    }

    fun dismissSheet() {
        shownSheet.value = null
    }

    /** c1–c3: Mis citas' card, and Inicio's «Esperando confirmación». */
    suspend fun openDetail(appointment: PatientAppointment) {
        stack.value = listOf(ChangeRoute.DETAIL)
        detail.load(appointment)
    }

    /** The open detail read again — after f5, c3 shows the rating just sent. */
    fun reloadDetail() {
        val appointment = (detail.state.value as? CareLoad.Ready)?.content?.appointment ?: return
        launch { detail.load(appointment) }
    }

    /** f2 → f3, c1/c2 → f3. Mis citas' «Cambiar» comes here directly. */
    suspend fun changeTime(appointment: PatientAppointment) {
        shownSheet.value = null
        push(ChangeRoute.RESCHEDULE)
        reschedule.load(appointment)
    }

    /** f2 → f4, c1/c2 → f4. Mis citas' «Cancelar» comes here directly. */
    fun cancelAppointment(appointment: PatientAppointment) {
        shownSheet.value = null
        cancel.start(appointment)
        push(ChangeRoute.CANCEL)
    }

    /** A link takes the person elsewhere (ADR-0053): whatever this flow had open closes, a half-chosen cancel too. */
    fun closeForLink() {
        shownSheet.value = null
        cancel.clear()
        stack.value = emptyList()
    }

    /**
     * c1–c3 opened by a link or a notification (ADR-0053). The id has to be one of the patient's own appointments, as
     * the gateway lists them, or nothing opens: a link grants navigation, never access. `false` when it is not there.
     */
    suspend fun openDetailFor(ref: OpaqueRef): Boolean {
        val mine = (sources.appointments.appointments() as? CareResult.Ok)?.value ?: return false
        val appointment = mine.firstOrNull { it.id == ref.value } ?: return false
        openDetail(appointment)
        return true
    }

    /** One screen back; from the detail, back to the tab. */
    fun back() {
        if (stack.value.lastOrNull() == ChangeRoute.CANCEL) cancel.clear()
        stack.value = stack.value.dropLast(1)
    }

    fun moved(appointment: PatientAppointment) {
        stack.value = emptyList()
        finished.value = ChangeDone.Moved(appointment)
    }

    fun cancelled() {
        stack.value = emptyList()
        cancel.clear()
        finished.value = ChangeDone.Cancelled
    }

    /** c3's «Agendar de nuevo»: the same doctor as the directory lists them today, or null when they are not listed. */
    suspend fun doctorToBookAgain(appointment: PatientAppointment): DirectoryDoctor? =
        (sources.directory.doctor(appointment.doctor.id) as? CareResult.Ok)?.value

    private fun push(route: ChangeRoute) {
        stack.value = stack.value.filterNot { it == route } + route
    }

    fun doneSeen() {
        finished.value = null
    }

    /** f4's warning names the change as the better road; it opens f3 for the same appointment. */
    suspend fun changeInstead() {
        val appointment = cancel.state.value?.appointment ?: return
        cancel.clear()
        stack.value = stack.value.filterNot { it == ChangeRoute.CANCEL }
        if (appointment.status in setOf(AppointmentStatus.CONFIRMED, AppointmentStatus.REQUESTED)) {
            changeTime(appointment)
        }
    }
}
