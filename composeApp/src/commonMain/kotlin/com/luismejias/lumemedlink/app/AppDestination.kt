package com.luismejias.lumemedlink.app

/**
 * Where the shell is. A closed set: the app is always in exactly one of these, decided by session
 * state, never by a screen navigating imperatively (mirror of LumeMed's coordinator/host split).
 */
internal sealed interface AppDestination {
    /** No session — the doctor must sign in. */
    data object Login : AppDestination

    /** A session exists but the inactivity lock is engaged: biometric re-auth required (F4). */
    data object Locked : AppDestination

    /** A session exists and is unlocked. */
    data object Home : AppDestination
}

/**
 * Pure resolution of destination from state — the whole navigation policy in one testable place.
 * Order matters: no session wins over locked (you cannot be "locked out" of nothing), and locked
 * wins over home (a live session still gates behind re-auth). The test pins every combination.
 */
internal fun resolveDestination(hasSession: Boolean, locked: Boolean): AppDestination = when {
    !hasSession -> AppDestination.Login
    locked -> AppDestination.Locked
    else -> AppDestination.Home
}
