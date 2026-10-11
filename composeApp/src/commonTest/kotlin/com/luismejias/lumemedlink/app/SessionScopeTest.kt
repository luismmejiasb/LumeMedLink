package com.luismejias.lumemedlink.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class SessionScopeTest {
    private class Probe : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }

    @Test
    fun aResetClearsEveryViewModelOfThePreviousPerson() {
        val scope = SessionScope()
        val before = scope.owner.value
        val probe = ViewModelProvider.create(before, viewModelFactory { initializer { Probe() } })[Probe::class]
        scope.reset()
        assertTrue(probe.cleared, "the previous patient's tabs must not survive in memory (§8.13)")
        assertNotSame(before, scope.owner.value)
        val factory = viewModelFactory { initializer { Probe() } }
        val fresh = ViewModelProvider.create(scope.owner.value, factory)[Probe::class]
        assertNotSame(probe, fresh)
    }
}
