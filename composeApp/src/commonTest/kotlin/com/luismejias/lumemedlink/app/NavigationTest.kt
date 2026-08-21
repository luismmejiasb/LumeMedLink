package com.luismejias.lumemedlink.app

import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationTest {

    @Test
    fun noSessionAlwaysResolvesToLogin() {
        assertEquals(AppDestination.Login, resolveDestination(hasSession = false, locked = false))
        assertEquals(
            AppDestination.Login,
            resolveDestination(hasSession = false, locked = true),
            "you cannot be locked out of a session that does not exist",
        )
    }

    @Test
    fun sessionAndLockedResolvesToLocked() {
        assertEquals(AppDestination.Locked, resolveDestination(hasSession = true, locked = true))
    }

    @Test
    fun sessionAndUnlockedResolvesToHome() {
        assertEquals(AppDestination.Home, resolveDestination(hasSession = true, locked = false))
    }
}
