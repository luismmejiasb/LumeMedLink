package com.luismejias.lumemedlink.features.patient.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.PatientCareGateway
import com.luismejias.lumemedlink.core.care.PatientProfile
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.core.screen.allOf
import com.luismejias.lumemedlink.core.screen.markLoading
import com.luismejias.lumemedlink.core.screen.ok
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What Configuración draws (p7). */
internal data class SettingsContent(
    val profile: PatientProfile,
    /** "24 h y 2 h antes", largest first. */
    val remindersHoursBefore: List<Int>,
    val sentRatings: Int,
) {
    override fun toString(): String = "SettingsContent(redacted)"
}

/** Configuración's owner (ADR-0045). */
internal class SettingsViewModel(private val care: PatientCareGateway) : ViewModel() {
    private val current = MutableStateFlow<CareLoad<SettingsContent>>(CareLoad.Loading)
    val state: StateFlow<CareLoad<SettingsContent>> = current.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        current.markLoading()
        val profile = care.profile()
        val policy = care.policy()
        val ratings = care.sentRatingCount()
        current.value = allOf(profile, policy, ratings) {
            SettingsContent(
                profile = profile.ok,
                remindersHoursBefore = policy.ok.remindersHoursBefore.sortedDescending(),
                sentRatings = ratings.ok,
            )
        }
    }
}
