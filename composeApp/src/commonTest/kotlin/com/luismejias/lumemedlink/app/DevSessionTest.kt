package com.luismejias.lumemedlink.app

import com.luismejias.lumemedlink.core.security.SecurityEventEmitter
import com.luismejias.lumemedlink.core.session.Clock
import com.luismejias.lumemedlink.core.session.ElapsedClock
import com.luismejias.lumemedlink.core.session.FailedAttemptLedger
import com.luismejias.lumemedlink.core.session.InactivityLock
import com.luismejias.lumemedlink.core.session.RefreshClient
import com.luismejias.lumemedlink.core.session.SecureStore
import com.luismejias.lumemedlink.core.session.SecureStoreKey
import com.luismejias.lumemedlink.core.session.SessionEntryOutcome
import com.luismejias.lumemedlink.core.session.SessionEntryRefusal
import com.luismejias.lumemedlink.core.session.SessionLock
import com.luismejias.lumemedlink.core.session.SessionManager
import com.luismejias.lumemedlink.core.session.SessionTokens
import com.luismejias.lumemedlink.core.session.TokenStore
import com.luismejias.lumemedlink.core.session.UnlockGate
import com.luismejias.lumemedlink.core.session.UnlockOutcome
import com.luismejias.lumemedlink.core.telemetry.TelemetryCollection
import com.luismejias.lumemedlink.core.telemetry.TelemetryConsent
import com.luismejias.lumemedlink.features.auth.flow.AccessRole
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class DevStore(private val failOn: String? = null) : SecureStore {
    val entries = mutableMapOf<String, String>()

    override suspend fun put(key: String, value: String) {
        if (key == failOn) error("synthetic store failure")
        entries[key] = value
    }

    override suspend fun get(key: String): String? = entries[key]

    override suspend fun remove(key: String) {
        entries.remove(key)
    }

    override suspend fun wipe() = entries.clear()
}

private class DevGate(private val canEnroll: Boolean) : UnlockGate {
    var enrolled = false

    override suspend fun enroll(): Boolean = canEnroll.also { enrolled = it }

    override suspend fun unlock(): UnlockOutcome = UnlockOutcome.Unlocked

    override suspend fun clear() {
        enrolled = false
    }
}

private class Fixture(canEnroll: Boolean = true, failOn: String? = null) {
    val store = DevStore(failOn)
    val gate = DevGate(canEnroll)
    val collecting = mutableListOf<Boolean>()
    val consent = TelemetryConsent(
        store,
        object : TelemetryCollection {
            override fun setCollectionEnabled(enabled: Boolean) {
                collecting += enabled
            }
        },
    )
    private val clock = object : Clock {
        override fun nowEpochMillis(): Long = 1_000_000L
    }
    val manager = SessionManager(
        TokenStore(store),
        object : RefreshClient {
            override suspend fun refresh(refreshToken: String): SessionTokens? = null
        },
        clock,
    )
    val lock = SessionLock(
        InactivityLock(
            300_000L,
            clock,
            object : ElapsedClock {
                override fun elapsedMillis(): Long = 0L
            },
        ),
        gate,
        FailedAttemptLedger(store),
        SecurityEventEmitter {},
    )

    suspend fun begin(role: AccessRole = AccessRole.PATIENT) =
        beginDevSession(role, manager, store, gate, lock, consent)
}

/** A debug build's session (ADR-0066): the real entry, with its side kept and the sign-up's consent carried across. */
class DevSessionTest {
    @Test
    fun aSessionBeginsWithItsBiometricKeyItsSideAndAnOpenLock() = runTest {
        val f = Fixture()
        assertEquals(SessionEntryOutcome.Established, f.begin(AccessRole.DOCTOR))
        assertTrue(f.gate.enrolled, "the biometric key is made")
        assertTrue(f.manager.hasSession())
        assertFalse(f.lock.isLocked(), "the lock opens with the session")
        assertEquals(AccessRole.DOCTOR, SessionRoleMark(f.store).read())
    }

    @Test
    fun theConsentGivenInTheSignUpSurvivesTheEraseAndThePreviousSessionsRemainsDoNot() = runTest {
        val f = Fixture()
        f.consent.setGranted(true)
        f.store.put(SecureStoreKey.NOTICES_SEEN_UNTIL.storageKey, "42")
        f.begin()
        assertTrue(f.consent.granted(), "the answer belongs to this session")
        assertNull(f.store.get(SecureStoreKey.NOTICES_SEEN_UNTIL.storageKey), "the last session's mark is gone")
    }

    @Test
    fun withoutBiometricsThereIsNoSessionAndNothingStays() = runTest {
        val f = Fixture(canEnroll = false)
        assertEquals(SessionEntryOutcome.Refused(SessionEntryRefusal.TIER2_UNAVAILABLE), f.begin())
        assertFalse(f.manager.hasSession())
        assertNull(SessionRoleMark(f.store).read())
    }

    @Test
    fun aSideThatCannotBeSavedLeavesNoHalfSession() = runTest {
        val f = Fixture(failOn = SecureStoreKey.SESSION_ROLE.storageKey)
        assertEquals(SessionEntryOutcome.Refused(SessionEntryRefusal.SESSION_NOT_SAVED), f.begin())
        assertFalse(f.manager.hasSession(), "the tokens written before were erased again")
        assertFalse(f.gate.enrolled, "and the key with them")
        assertTrue(f.lock.isLocked())
    }

    @Test
    fun aSideThatIsNotOneOfOursReadsAsNone() = runTest {
        val store = DevStore().apply { put(SecureStoreKey.SESSION_ROLE.storageKey, "ADMIN") }
        assertNull(SessionRoleMark(store).read())
    }
}
