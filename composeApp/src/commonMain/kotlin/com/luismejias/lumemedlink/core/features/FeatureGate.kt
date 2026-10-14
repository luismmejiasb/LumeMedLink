package com.luismejias.lumemedlink.core.features

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Which features are available now (ADR-0060): the role opens, the flag closes, and [isAvailable] decides. View models
 * get this by constructor; no screen reads the role or Remote Config by itself.
 *
 * It decides what is drawn, never what is allowed: the backend's tier checks stay the authority.
 */
internal class FeatureGate(roles: RoleSource, flags: FlagSource, scope: CoroutineScope) {
    /**
     * The available features. Its first value is computed from the sources' current values, not from an empty set
     * that would flash every screen closed, nor from a full one that would flash a feature open.
     */
    val available: StateFlow<Set<Feature>> = combine(roles.role, flags.flags, ::availableFeatures)
        .stateIn(scope, SharingStarted.Eagerly, availableFeatures(roles.role.value, flags.flags.value))

    fun isAvailable(feature: Feature): Boolean = feature in available.value
}

/** Every feature [isAvailable] opens for [role] under [flags]. */
internal fun availableFeatures(role: RoleState, flags: FlagValues): Set<Feature> =
    Feature.entries.filterTo(mutableSetOf()) { isAvailable(it, role, flags::get) }
