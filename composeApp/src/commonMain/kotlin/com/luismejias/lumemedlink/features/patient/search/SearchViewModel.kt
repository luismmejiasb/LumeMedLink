package com.luismejias.lumemedlink.features.patient.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.PatientCareGateway
import com.luismejias.lumemedlink.core.care.RecentSearch
import com.luismejias.lumemedlink.features.patient.parts.CareLoad
import com.luismejias.lumemedlink.features.patient.parts.markLoading
import com.luismejias.lumemedlink.features.patient.parts.toLoad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Buscar's form (p2): both choices are required before "Buscar médicos" turns on. */
internal data class SearchCriteria(val specialty: String? = null, val commune: String? = null) {
    val complete: Boolean get() = specialty != null && commune != null

    override fun toString(): String = "SearchCriteria(complete=$complete)"
}

/** Buscar's owner (ADR-0045). */
internal class SearchViewModel(private val care: PatientCareGateway) : ViewModel() {
    private val recentLoad = MutableStateFlow<CareLoad<List<RecentSearch>>>(CareLoad.Loading)
    val recent: StateFlow<CareLoad<List<RecentSearch>>> = recentLoad.asStateFlow()

    private val chosen = MutableStateFlow(SearchCriteria())
    val criteria: StateFlow<SearchCriteria> = chosen.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        recentLoad.markLoading()
        recentLoad.value = care.recentSearches().toLoad { it }
    }

    fun specialtyChosen(name: String) {
        chosen.value = chosen.value.copy(specialty = name)
    }

    fun communeChosen(name: String) {
        chosen.value = chosen.value.copy(commune = name)
    }

    /** A recent search fills both at once. */
    fun recentChosen(search: RecentSearch) {
        chosen.value = SearchCriteria(search.specialty, search.commune)
    }
}
