package com.luismejias.lumemedlink.features.patient.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.care.PatientCareGateway
import com.luismejias.lumemedlink.core.care.PatientProfile
import com.luismejias.lumemedlink.core.care.standInPatientCare
import com.luismejias.lumemedlink.core.screen.CareLoad
import com.luismejias.lumemedlink.core.screen.StandIn
import com.luismejias.lumemedlink.core.screen.allOf
import com.luismejias.lumemedlink.core.screen.markLoading
import com.luismejias.lumemedlink.core.screen.ok
import com.luismejias.lumemedlink.core.telemetry.TelemetryConsent
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
internal class SettingsViewModel(private val care: PatientCareGateway, private val consent: TelemetryConsent) :
    ViewModel() {
    private val current = MutableStateFlow<CareLoad<SettingsContent>>(CareLoad.Loading)
    val state: StateFlow<CareLoad<SettingsContent>> = current.asStateFlow()

    /** The screen's own shape while it waits (ADR-0055). */
    private val standInContent = StandIn<SettingsContent>()
    val standIn: StateFlow<SettingsContent?> = standInContent.content

    private val telemetryGranted = MutableStateFlow(false)

    /** «Errores y estadísticas»: the consent of ADR-0052, withdrawn or given here. */
    val telemetry: StateFlow<Boolean> = telemetryGranted.asStateFlow()

    init {
        refresh()
        viewModelScope.launch { telemetryGranted.value = consent.granted() }
    }

    fun telemetryChanged(granted: Boolean) {
        telemetryGranted.value = granted
        viewModelScope.launch {
            consent.setGranted(granted)
            telemetryGranted.value = consent.granted()
        }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    suspend fun load() {
        standInContent.fill { read(standInPatientCare()) }
        current.markLoading()
        current.value = read(care)
    }

    private suspend fun read(care: PatientCareGateway): CareLoad<SettingsContent> {
        val profile = care.profile()
        val policy = care.policy()
        val ratings = care.sentRatingCount()
        return allOf(profile, policy, ratings) {
            SettingsContent(
                profile = profile.ok,
                remindersHoursBefore = policy.ok.remindersHoursBefore.sortedDescending(),
                sentRatings = ratings.ok,
            )
        }
    }
}
