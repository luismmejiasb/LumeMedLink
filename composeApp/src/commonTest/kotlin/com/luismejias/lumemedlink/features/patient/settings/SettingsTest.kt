package com.luismejias.lumemedlink.features.patient.settings

import com.luismejias.lumemedlink.core.appearance.Appearance
import com.luismejias.lumemedlink.core.appearance.LanguageChoice
import com.luismejias.lumemedlink.core.appearance.ThemeChoice
import com.luismejias.lumemedlink.core.care.PatientCareGateway
import com.luismejias.lumemedlink.core.care.ScriptedPatientCare
import com.luismejias.lumemedlink.core.session.SecureStoreKey
import com.luismejias.lumemedlink.core.telemetry.MemorySecureStore
import com.luismejias.lumemedlink.core.telemetry.RecordedTelemetry
import com.luismejias.lumemedlink.core.telemetry.TelemetryConsent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A Configuración over [care], with its stores in memory and a licences file of two synthetic lines. */
internal fun settingsModel(
    care: PatientCareGateway,
    store: MemorySecureStore = MemorySecureStore(),
    licenses: String = "Alpha\tMIT License\nBeta\tApache License 2.0\n",
): SettingsViewModel =
    SettingsViewModel(care, TelemetryConsent(store, RecordedTelemetry()), Appearance(store)) { licenses }

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsTest {
    private val dispatcher = StandardTestDispatcher()
    private val care = ScriptedPatientCare(latency = {})

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun pagesPushOverConfiguracionAndBackPopsThem() = runTest(dispatcher) {
        val settings = settingsModel(care)
        assertTrue(settings.routes.value.isEmpty())
        settings.open(SettingsRoute.PRIVACY)
        settings.open(SettingsRoute.TERMS)
        assertEquals(listOf(SettingsRoute.PRIVACY, SettingsRoute.TERMS), settings.routes.value)
        settings.back()
        assertEquals(listOf(SettingsRoute.PRIVACY), settings.routes.value)
        settings.back()
        settings.back()
        assertTrue(settings.routes.value.isEmpty(), "a back at the root stays at the root")
    }

    @Test
    fun signingOutAsksFirstAndCancelLeavesItAsked() = runTest(dispatcher) {
        val settings = settingsModel(care)
        assertFalse(settings.signOutConfirming.value)
        settings.askSignOut()
        assertTrue(settings.signOutConfirming.value)
        settings.signOutAnswered()
        assertFalse(settings.signOutConfirming.value)
    }

    @Test
    fun themeAndLanguageStartOnThePhonesAndSurviveARelaunch() = runTest(dispatcher) {
        val store = MemorySecureStore()
        val settings = settingsModel(care, store)
        advanceUntilIdle()
        assertEquals(ThemeChoice.SYSTEM, settings.theme.value)
        assertEquals(LanguageChoice.SYSTEM, settings.language.value)
        settings.themeChosen(ThemeChoice.DARK)
        settings.languageChosen(LanguageChoice.ENGLISH)
        advanceUntilIdle()
        val relaunched = Appearance(store).also { it.restore() }
        assertEquals(ThemeChoice.DARK, relaunched.theme.value)
        assertEquals(LanguageChoice.ENGLISH, relaunched.language.value)
    }

    @Test
    fun choosingThePhonesAgainLeavesNothingStored() = runTest(dispatcher) {
        val store = MemorySecureStore()
        val appearance = Appearance(store)
        appearance.choose(ThemeChoice.LIGHT)
        appearance.choose(LanguageChoice.SPANISH)
        appearance.choose(ThemeChoice.SYSTEM)
        appearance.choose(LanguageChoice.SYSTEM)
        assertNull(store.get(SecureStoreKey.APPEARANCE_THEME.storageKey))
        assertNull(store.get(SecureStoreKey.APPEARANCE_LANGUAGE.storageKey))
    }

    @Test
    fun theWipeHandsBothBackToThePhone() = runTest(dispatcher) {
        val store = MemorySecureStore()
        val appearance = Appearance(store)
        appearance.choose(ThemeChoice.DARK)
        appearance.choose(LanguageChoice.ENGLISH)
        SecureStoreKey.entries.forEach { store.remove(it.storageKey) }
        appearance.restore()
        assertEquals(ThemeChoice.SYSTEM, appearance.theme.value)
        assertEquals(LanguageChoice.SYSTEM, appearance.language.value)
    }

    @Test
    fun anUnknownStoredValueReadsAsThePhones() = runTest(dispatcher) {
        val store = MemorySecureStore()
        store.put(SecureStoreKey.APPEARANCE_THEME.storageKey, "sepia")
        val appearance = Appearance(store).also { it.restore() }
        assertEquals(ThemeChoice.SYSTEM, appearance.theme.value)
    }

    @Test
    fun licencesAreReadWhenTheirPageOpens() = runTest(dispatcher) {
        val settings = settingsModel(care)
        advanceUntilIdle()
        assertTrue(settings.licenses.value.isEmpty(), "not read before anyone asks")
        settings.open(SettingsRoute.LICENSES)
        advanceUntilIdle()
        assertEquals(
            listOf(OpenSourceLicense("Alpha", "MIT License"), OpenSourceLicense("Beta", "Apache License 2.0")),
            settings.licenses.value,
        )
    }

    @Test
    fun aLicenceLineWithoutBothHalvesIsDropped() {
        assertEquals(
            listOf(OpenSourceLicense("Alpha", "MIT License")),
            parseLicenses("Alpha\tMIT License\nno tab here\n\t\nBeta\t\n"),
        )
    }
}
