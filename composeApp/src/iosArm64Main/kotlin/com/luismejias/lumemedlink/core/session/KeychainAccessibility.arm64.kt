package com.luismejias.lumemedlink.core.session

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFStringRef
import platform.Security.kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly

/**
 * DEVICE build: the ADR-0005 floor. Known consequence, accepted as fail-closed: removing the
 * device passcode DELETES the item silently — the session dies and the doctor logs in again.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual val tokenTierKeychainAccessibility: CFStringRef? =
    kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly
