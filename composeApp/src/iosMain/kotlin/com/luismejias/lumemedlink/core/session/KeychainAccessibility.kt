package com.luismejias.lumemedlink.core.session

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFStringRef

/**
 * Tier-1 accessibility class, per target (ADR-0005): the device build pins
 * `WhenPasscodeSetThisDeviceOnly`; the simulator build — which has no passcode — compiles in a
 * relaxed class INSTEAD of branching at runtime, so the production binary cannot carry the
 * relaxed path at all ("simulator deviation compiled out", as LumeMed does).
 */
@OptIn(ExperimentalForeignApi::class)
internal expect val tokenTierKeychainAccessibility: CFStringRef?
