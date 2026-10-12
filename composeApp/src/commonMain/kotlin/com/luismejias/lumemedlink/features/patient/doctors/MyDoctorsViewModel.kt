package com.luismejias.lumemedlink.features.patient.doctors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.MyDoctor
import com.luismejias.lumemedlink.core.care.PatientCareGateway
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.core.screen.folded
import com.luismejias.lumemedlink.core.screen.markLoading
import com.luismejias.lumemedlink.core.screen.toLoad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What Mis médicos draws (p6, e9): every doctor, and the ones the filter keeps. */
internal data class MyDoctorsContent(val all: List<MyDoctor>, val query: String = "") {
    val shown: List<MyDoctor> get() = all.filter { matches(it, query) }

    override fun toString(): String = "MyDoctorsContent(all=${all.size}, filtered=${query.isNotEmpty()})"

    private companion object {
        /** By name or specialty, ignoring case and accents: "rendon" finds "Rendón". */
        fun matches(entry: MyDoctor, query: String): Boolean {
            val wanted = query.folded()
            if (wanted.isEmpty()) return true
            return listOf(entry.doctor.displayName, entry.doctor.specialty).any { it.folded().contains(wanted) }
        }
    }
}

/** Mis médicos' owner (ADR-0045). The filter runs here, over what was read — no request per keystroke. */
internal class MyDoctorsViewModel(private val care: PatientCareGateway) : ViewModel() {
    private val current = MutableStateFlow<CareLoad<MyDoctorsContent>>(CareLoad.Loading)
    val state: StateFlow<CareLoad<MyDoctorsContent>> = current.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        current.markLoading()
        val query = (current.value as? CareLoad.Ready)?.content?.query.orEmpty()
        current.value = care.myDoctors().toLoad { MyDoctorsContent(it, query) }
    }

    fun queryChanged(query: String) {
        val ready = current.value as? CareLoad.Ready ?: return
        current.value = CareLoad.Ready(ready.content.copy(query = query))
    }
}
