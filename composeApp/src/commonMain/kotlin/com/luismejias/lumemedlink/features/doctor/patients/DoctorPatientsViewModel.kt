package com.luismejias.lumemedlink.features.doctor.patients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.DoctorCareGateway
import com.luismejias.lumemedlink.core.care.PatientContact
import com.luismejias.lumemedlink.core.care.PatientRoster
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.core.screen.folded
import com.luismejias.lumemedlink.core.screen.markLoading
import com.luismejias.lumemedlink.core.screen.toLoad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** m2: the doctor's patients as contacts, narrowed by a name or a RUT, grouped by the first letter of the name. */
internal data class PatientsContent(val roster: PatientRoster, val query: String = "") {
    val shown: List<PatientContact>
        get() {
            val wanted = query.folded()
            val digits = query.filter { it.isDigit() || it == 'k' || it == 'K' }.uppercase()
            return roster.contacts.filter { contact ->
                wanted.isEmpty() ||
                    wanted in contact.displayName.folded() ||
                    (
                        digits.length >= MIN_RUT_DIGITS &&
                            contact.rut?.let { "${it.body}${it.checkDigit}".startsWith(digits) } == true
                        )
            }.sortedBy { it.displayName.folded() }
        }

    /** «A», «C»…: the sections m2 draws, each with its patients. */
    val sections: List<Pair<String, List<PatientContact>>>
        get() = shown.groupBy { it.givenNames.folded().first().uppercaseChar().toString() }.toList()

    override fun toString(): String = "PatientsContent(shown=${shown.size})"

    private companion object {
        /** A RUT search starts at a few digits, so a single digit in a name query never matches every RUT. */
        const val MIN_RUT_DIGITS = 3
    }
}

/** The Pacientes tab's owner. The query lives in memory only, and a name typed there never leaves this screen. */
internal class DoctorPatientsViewModel(private val care: DoctorCareGateway) : ViewModel() {
    private val current = MutableStateFlow<CareLoad<PatientsContent>>(CareLoad.Loading)
    val state: StateFlow<CareLoad<PatientsContent>> = current.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        current.markLoading()
        current.value = care.patients().toLoad { PatientsContent(it) }
    }

    fun queryChanged(query: String) {
        val ready = current.value as? CareLoad.Ready ?: return
        current.value = CareLoad.Ready(ready.content.copy(query = query))
    }
}
