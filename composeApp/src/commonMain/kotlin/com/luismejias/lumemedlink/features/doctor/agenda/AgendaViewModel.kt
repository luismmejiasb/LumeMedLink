package com.luismejias.lumemedlink.features.doctor.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.AgendaEntry
import com.luismejias.lumemedlink.core.care.ChangeFailure
import com.luismejias.lumemedlink.core.care.ChangeResult
import com.luismejias.lumemedlink.core.care.DoctorCareGateway
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.core.screen.allOf
import com.luismejias.lumemedlink.core.screen.markLoading
import com.luismejias.lumemedlink.core.screen.ok
import com.luismejias.lumemedlink.shared.CalendarDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** m1: the week, the requests waiting for an answer, and the chosen day's appointments. */
internal data class AgendaContent(
    val today: CalendarDate,
    val day: CalendarDate,
    /** Monday to Sunday of the week the chosen day is in. */
    val week: List<CalendarDate>,
    val requests: List<AgendaEntry>,
    val entries: List<AgendaEntry>,
) {
    override fun toString(): String = "AgendaContent(requests=${requests.size}, entries=${entries.size})"
}

/** The Agenda tab's owner (ADR-0045: a ViewModel per tab root). A simple view: LumeMed keeps the full agenda. */
internal class AgendaViewModel(private val care: DoctorCareGateway) : ViewModel() {
    private val current = MutableStateFlow<CareLoad<AgendaContent>>(CareLoad.Loading)
    val state: StateFlow<CareLoad<AgendaContent>> = current.asStateFlow()

    /** Why the last confirm or reject did not go through, until it is seen. */
    private val refusal = MutableStateFlow<ChangeFailure?>(null)
    val failure: StateFlow<ChangeFailure?> = refusal.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun launch(step: suspend AgendaViewModel.() -> Unit) {
        viewModelScope.launch { step() }
    }

    suspend fun load(day: CalendarDate = (current.value as? CareLoad.Ready)?.content?.day ?: care.today()) {
        current.markLoading()
        val requests = care.requests()
        val entries = care.agenda(day)
        current.value = allOf(requests, entries) {
            val monday = day.plusDays(1 - day.isoWeekday)
            AgendaContent(
                care.today(),
                day,
                (0 until DAYS_IN_WEEK).map {
                    monday.plusDays(it)
                },
                requests.ok,
                entries.ok,
            )
        }
    }

    suspend fun dayChosen(day: CalendarDate) = load(day)

    suspend fun confirm(entry: AgendaEntry) = answer(care.confirm(entry))

    suspend fun reject(entry: AgendaEntry) = answer(care.reject(entry))

    fun failureSeen() {
        refusal.value = null
    }

    private suspend fun answer(result: ChangeResult) {
        if (result is ChangeResult.Failed) refusal.value = result.failure else load()
    }

    private companion object {
        const val DAYS_IN_WEEK = 7
    }
}
