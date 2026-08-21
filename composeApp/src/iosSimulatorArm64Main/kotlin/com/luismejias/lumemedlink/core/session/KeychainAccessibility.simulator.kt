package com.luismejias.lumemedlink.core.session

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFStringRef
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly

/**
 * SIMULATOR build only: a simulator has no passcode, so the production class would refuse every
 * write and no test could exercise the real Keychain path. Still `ThisDeviceOnly` — the family
 * never touches synchronizable. This source set never reaches a store shelf.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual val tokenTierKeychainAccessibility: CFStringRef? =
    kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
