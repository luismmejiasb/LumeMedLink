package com.luismejias.lumemedlink.app

import androidx.lifecycle.Lifecycle
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivacyScreenTest {

    @Test
    fun coveredInEveryStateBelowResumed() {
        assertTrue(shouldCover(Lifecycle.State.INITIALIZED))
        assertTrue(shouldCover(Lifecycle.State.DESTROYED))
        assertTrue(shouldCover(Lifecycle.State.CREATED))
        assertTrue(shouldCover(Lifecycle.State.STARTED), "app-switcher snapshot happens at STARTED")
    }

    @Test
    fun uncoveredOnlyWhenResumed() {
        assertFalse(shouldCover(Lifecycle.State.RESUMED))
    }
}
