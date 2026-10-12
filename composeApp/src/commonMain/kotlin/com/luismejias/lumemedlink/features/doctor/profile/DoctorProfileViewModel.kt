package com.luismejias.lumemedlink.features.doctor.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.CareResult
import com.luismejias.lumemedlink.core.care.DoctorCareGateway
import com.luismejias.lumemedlink.core.care.DoctorProfile
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.core.screen.markLoading
import com.luismejias.lumemedlink.core.screen.toLoad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** m3's owner: the doctor's public profile and the one switch that shares their contact with every patient. */
internal class DoctorProfileViewModel(private val care: DoctorCareGateway) : ViewModel() {
    private val current = MutableStateFlow<CareLoad<DoctorProfile>>(CareLoad.Loading)
    val state: StateFlow<CareLoad<DoctorProfile>> = current.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun launch(step: suspend DoctorProfileViewModel.() -> Unit) {
        viewModelScope.launch { step() }
    }

    suspend fun load() {
        current.markLoading()
        current.value = care.profile().toLoad { it }
    }

    /** The switch moves at once; if the write fails, it moves back — what the patients see is the server's. */
    suspend fun shareContact(share: Boolean) {
        val ready = (current.value as? CareLoad.Ready)?.content ?: return
        current.value = CareLoad.Ready(ready.copy(sharesContact = share))
        if (care.shareContact(share) is CareResult.Failed) current.value = CareLoad.Ready(ready)
    }
}
