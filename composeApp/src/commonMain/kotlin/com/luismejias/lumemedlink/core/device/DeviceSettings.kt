package com.luismejias.lumemedlink.core.device

import androidx.compose.runtime.Composable

/**
 * The phone's own settings, where a person enrols a face or a fingerprint (1c-e1, "Abrir ajustes del
 * teléfono"). A screen asks this interface and never knows the platform (§4, ADR-0008).
 *
 * It only OPENS the settings: nothing comes back, and nothing about the phone is read.
 */
internal fun interface DeviceSettings {
    fun openBiometricSettings()
}

@Composable
internal expect fun rememberDeviceSettings(): DeviceSettings
