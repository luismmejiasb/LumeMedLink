package com.luismejias.lumemedlink.core.session

import platform.Security.errSecItemNotFound
import kotlin.test.Test
import kotlin.test.assertEquals

// OSStatus values from SecBase.h, spelled out on purpose: they are the ones the family has confused
// before, and a test is where the number and its meaning get written next to each other.
private const val USER_CANCELED = -128
private const val AUTH_FAILED = -25293
private const val NOT_AVAILABLE = -25291
private const val INTERACTION_NOT_ALLOWED = -25308
private const val PARAM = -50

/**
 * "Cancelling costs nothing" on iOS, pinned where it can break (task `0010`): the status the Secure
 * Enclave answers when the Face ID sheet is dismissed must arrive at the policy as `Cancelled`. No
 * keychain is needed to ask what a status MEANS, so this runs in the hostless test process that cannot
 * reach one.
 */
class KeychainStatusMappingTest {

    @Test
    fun dismissingTheSheetIsNotAMiss() {
        assertEquals(UnlockOutcome.Cancelled, unlockOutcomeForKeychainStatus(USER_CANCELED))
    }

    @Test
    fun aWrongFaceIsAMiss() {
        assertEquals(UnlockOutcome.Failed, unlockOutcomeForKeychainStatus(AUTH_FAILED))
    }

    @Test
    fun anItemDestroyedByReEnrollmentIsInvalidated() {
        assertEquals(UnlockOutcome.Invalidated, unlockOutcomeForKeychainStatus(errSecItemNotFound))
    }

    @Test
    fun aKeychainThatCannotAnswerNowCostsNothing() {
        // ADR-0040: the phone locked mid-read is not a failure and not the end of the session.
        listOf(NOT_AVAILABLE, INTERACTION_NOT_ALLOWED).forEach { status ->
            assertEquals(UnlockOutcome.NotNow, unlockOutcomeForKeychainStatus(status), "status $status")
        }
    }

    @Test
    fun aStatusNobodyClassifiedCountsAgainstTheAttacker() {
        assertEquals(UnlockOutcome.Failed, unlockOutcomeForKeychainStatus(PARAM))
    }
}
