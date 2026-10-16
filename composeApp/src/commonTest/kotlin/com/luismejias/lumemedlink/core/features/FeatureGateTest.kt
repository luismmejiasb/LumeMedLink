package com.luismejias.lumemedlink.core.features

import com.luismejias.lumemedlink.core.telemetry.RemoteFlag
import com.luismejias.lumemedlink.core.telemetry.RemoteFlags
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The patient's features, written out by hand: the catalog is checked against this list, not against itself. */
private val PATIENT_FEATURES = setOf(
    Feature.APPOINTMENTS,
    Feature.APPOINTMENT_CHANGES,
    Feature.DOCTOR_SEARCH,
    Feature.BOOKING,
    Feature.MY_DOCTORS,
    Feature.PROFILE_EDIT,
    Feature.CONTACT_CHANGE,
    Feature.RATINGS,
    Feature.NOTICES,
    Feature.OVERBOOK_REQUESTS,
)

/** The doctor's features, by hand for the same reason. */
private val DOCTOR_FEATURES = setOf(
    Feature.DOCTOR_AGENDA,
    Feature.DOCTOR_REQUESTS,
    Feature.DOCTOR_PATIENTS,
    Feature.DOCTOR_CONTACT_SHARING,
)

private val PATIENT = RoleState.Known(Role.PATIENT)
private val DOCTOR = RoleState.Known(Role.DOCTOR)

/** Every flag on, overriding whatever default the catalog compiles in. */
private val ALL_ON = FlagValues(RemoteFlag.entries.associateWith { true })

/** Remote Config with values a test sets, and a fetch that can be made to fail or to hang. */
private class FakeRemoteFlags(var values: Map<RemoteFlag, Boolean> = emptyMap()) : RemoteFlags {
    var failure: Exception? = null
    var fetches = 0

    override fun flag(flag: RemoteFlag): Boolean = values[flag] ?: flag.default

    override suspend fun refreshFlags() {
        fetches += 1
        failure?.let { throw it }
    }
}

private class FixedFlagSource(initial: FlagValues = ALL_ON) : FlagSource {
    val state = MutableStateFlow(initial)
    override val flags = state

    override suspend fun refresh() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureGateTest {
    @Test
    fun theCatalogSplitsEveryFeatureBetweenThePatientAndTheDoctor() {
        assertEquals(Feature.entries.toSet(), PATIENT_FEATURES + DOCTOR_FEATURES)
        assertTrue((PATIENT_FEATURES intersect DOCTOR_FEATURES).isEmpty())
    }

    @Test
    fun aPatientGetsExactlyThePatientsFeatures() {
        assertEquals(PATIENT_FEATURES, availableFeatures(PATIENT, ALL_ON))
    }

    @Test
    fun aDoctorGetsExactlyTheDoctorsFeatures() {
        assertEquals(DOCTOR_FEATURES, availableFeatures(DOCTOR, ALL_ON))
    }

    @Test
    fun anUnknownRoleOpensNothingEvenWithEveryFlagOn() {
        assertEquals(emptySet(), availableFeatures(RoleState.Unknown, ALL_ON))
    }

    @Test
    fun aFlagClosesItsOwnFeatureAndNoOther() {
        Feature.entries.forEach { feature ->
            val role = if (feature in PATIENT_FEATURES) PATIENT else DOCTOR
            val everyOther = if (feature in PATIENT_FEATURES) PATIENT_FEATURES - feature else DOCTOR_FEATURES - feature
            val off = FlagValues(RemoteFlag.entries.associateWith { it != ruleOf(feature).flag })
            assertEquals(everyOther, availableFeatures(role, off), "switching off $feature's flag")
        }
    }

    @Test
    fun aFlagNeverOpensAFeatureOfAnotherRole() {
        DOCTOR_FEATURES.forEach { assertFalse(isAvailable(it, PATIENT) { true }, "$it for a patient") }
        PATIENT_FEATURES.forEach { assertFalse(isAvailable(it, DOCTOR) { true }, "$it for a doctor") }
    }

    @Test
    fun everyFlagBelongsToExactlyOneFeature() {
        val flags = Feature.entries.map { ruleOf(it).flag }
        assertEquals(flags.size, flags.toSet().size, "two features share a flag")
        assertEquals(RemoteFlag.entries.toSet(), flags.toSet(), "a flag switches no feature")
    }

    @Test
    fun everyFlagKeyIsUniqueAndNamesItsFeature() {
        assertEquals(RemoteFlag.entries.size, RemoteFlag.entries.map { it.key }.toSet().size)
        Feature.entries.forEach { assertEquals("feature_${it.name.lowercase()}", ruleOf(it).flag.key) }
    }

    @Test
    fun aFlagWithoutARemoteValueAnswersItsCompiledDefault() {
        RemoteFlag.entries.forEach { assertEquals(it.default, FlagValues()[it]) }
    }

    @Test
    fun theGateStartsFromTheSourcesCurrentValuesNotFromAnEmptySet() = runTest {
        val roles = SessionRoleSource().apply { signedIn(Role.PATIENT) }
        val gate = FeatureGate(roles, FixedFlagSource(), backgroundScope)

        assertEquals(PATIENT_FEATURES, gate.available.value)
    }

    @Test
    fun theGateFollowsTheRoleArrivingLateAndLeavingAtSignOut() = runTest(UnconfinedTestDispatcher()) {
        val roles = SessionRoleSource()
        val gate = FeatureGate(roles, FixedFlagSource(), backgroundScope)
        assertEquals(emptySet(), gate.available.value)

        roles.signedIn(Role.DOCTOR)
        assertEquals(DOCTOR_FEATURES, gate.available.value)
        assertTrue(gate.isAvailable(Feature.DOCTOR_AGENDA))

        roles.signedOut()
        assertEquals(emptySet(), gate.available.value)
        assertFalse(gate.isAvailable(Feature.DOCTOR_AGENDA))
    }

    @Test
    fun theGateClosesAFeatureWhenItsFlagTurnsOff() = runTest(UnconfinedTestDispatcher()) {
        val remote = FakeRemoteFlags()
        val flags = RemoteConfigFlagSource(remote)
        val gate = FeatureGate(SessionRoleSource().apply { signedIn(Role.PATIENT) }, flags, backgroundScope)
        assertTrue(gate.isAvailable(Feature.BOOKING))

        remote.values = mapOf(RemoteFlag.FEATURE_BOOKING to false)
        flags.refresh()

        assertFalse(gate.isAvailable(Feature.BOOKING))
        assertTrue(gate.isAvailable(Feature.DOCTOR_SEARCH))
    }

    @Test
    fun aFailedFetchKeepsTheLastValues() = runTest {
        val remote = FakeRemoteFlags(mapOf(RemoteFlag.FEATURE_RATINGS to false))
        val flags = RemoteConfigFlagSource(remote)
        remote.values = emptyMap()
        remote.failure = IllegalStateException("offline")

        flags.refresh()

        assertEquals(1, remote.fetches)
        assertFalse(flags.flags.value[RemoteFlag.FEATURE_RATINGS], "the value read before the failure stays")
    }

    @Test
    fun aCancelledCallerIsNotSwallowedByTheFetchsCatch() = runTest {
        // The caller is gone and the fetch fails in an ordinary way: the catch must hand back the cancellation
        // (ADR-0026), not carry on as if only the fetch had failed.
        val flags = RemoteConfigFlagSource(FakeRemoteFlags().apply { failure = IllegalStateException("offline") })
        var carriedOn = false
        val job = launch {
            coroutineContext[Job]!!.cancel()
            flags.refresh()
            carriedOn = true
        }
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertFalse(carriedOn, "a cancelled caller carried on after the refresh")
    }

    @Test
    fun aFailureThatIsNotTheCallersCancellationDoesNotEscape() = runTest {
        val remote = FakeRemoteFlags().apply { failure = IllegalStateException("bad config") }
        // Would throw if the catch did not hold: the refresh's contract is to keep the last values, silently.
        RemoteConfigFlagSource(remote).refresh()
        assertFailsWith<IllegalStateException> { remote.refreshFlags() }
    }
}
