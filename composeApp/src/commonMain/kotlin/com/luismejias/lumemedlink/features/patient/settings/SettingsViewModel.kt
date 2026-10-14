package com.luismejias.lumemedlink.features.patient.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luismejias.lumemedlink.core.appearance.Appearance
import com.luismejias.lumemedlink.core.appearance.LanguageChoice
import com.luismejias.lumemedlink.core.appearance.ThemeChoice
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

/** The pages Configuración pushes, each over the one before. */
internal enum class SettingsRoute { APPEARANCE, LANGUAGE, PRIVACY, TERMS, LICENSES }

/** One third party the app ships, as «Licencias» lists it (generated: Scripts/generate-licenses.py). */
internal data class OpenSourceLicense(val project: String, val license: String)

/** The generated list, one `project<TAB>licence` per line; a line without both halves is dropped, never guessed. */
internal fun parseLicenses(text: String): List<OpenSourceLicense> = text.lineSequence()
    .mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size == 2 && parts.all { it.isNotBlank() }) OpenSourceLicense(parts[0], parts[1]) else null
    }
    .toList()

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
internal class SettingsViewModel(
    private val care: PatientCareGateway,
    private val consent: TelemetryConsent,
    private val appearance: Appearance,
    /** The generated licences file, read from the app's resources. */
    private val readLicenses: suspend () -> String,
) : ViewModel() {
    private val current = MutableStateFlow<CareLoad<SettingsContent>>(CareLoad.Loading)
    val state: StateFlow<CareLoad<SettingsContent>> = current.asStateFlow()

    /** The screen's own shape while it waits (ADR-0055). */
    private val standInContent = StandIn<SettingsContent>()
    val standIn: StateFlow<SettingsContent?> = standInContent.content

    private val telemetryGranted = MutableStateFlow(false)

    /** «Errores y estadísticas»: the consent of ADR-0052, withdrawn or given here. */
    val telemetry: StateFlow<Boolean> = telemetryGranted.asStateFlow()

    private val stack = MutableStateFlow<List<SettingsRoute>>(emptyList())

    /** The pages over Configuración, the last in front; empty is Configuración itself. */
    val routes: StateFlow<List<SettingsRoute>> = stack.asStateFlow()

    private val confirming = MutableStateFlow(false)

    /** «Cerrar sesión» asks first (the author, 2026-10-13). */
    val signOutConfirming: StateFlow<Boolean> = confirming.asStateFlow()

    val theme: StateFlow<ThemeChoice> = appearance.theme
    val language: StateFlow<LanguageChoice> = appearance.language

    private val licenseList = MutableStateFlow<List<OpenSourceLicense>>(emptyList())
    val licenses: StateFlow<List<OpenSourceLicense>> = licenseList.asStateFlow()

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

    fun open(route: SettingsRoute) {
        stack.value = stack.value + route
        if (route == SettingsRoute.LICENSES && licenseList.value.isEmpty()) {
            viewModelScope.launch { licenseList.value = parseLicenses(readLicenses()) }
        }
    }

    fun back() {
        stack.value = stack.value.dropLast(1)
    }

    fun askSignOut() {
        confirming.value = true
    }

    fun signOutAnswered() {
        confirming.value = false
    }

    fun themeChosen(choice: ThemeChoice) {
        viewModelScope.launch { appearance.choose(choice) }
    }

    fun languageChosen(choice: LanguageChoice) {
        viewModelScope.launch { appearance.choose(choice) }
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
