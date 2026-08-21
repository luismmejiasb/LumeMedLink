package com.luismejias.lumemedlink.core.session

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.Security.SecAccessControlCreateWithFlags
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAccessControlBiometryCurrentSet
import platform.Security.kSecAttrAccessControl
import platform.Security.kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecRandomDefault
import platform.Security.kSecReturnData
import platform.Security.kSecUseDataProtectionKeychain
import platform.Security.kSecValueData

private const val KEYCHAIN_SERVICE = "com.luismejias.lumemedlink.session"
private const val UNLOCK_ACCOUNT = "unlock_secret_v1"
private const val SECRET_BYTES = 32

// OSStatus values that the SDK does not export as Kotlin symbols. Verified against SecBase.h
// (they are the two the family has gotten wrong before, so they are named, not inlined):
// dismissing the prompt must NEVER be mistaken for a wrong finger.
private const val ERR_SEC_USER_CANCELED = -128
private const val ERR_SEC_AUTH_FAILED = -25293
private const val ERR_SEC_NOT_AVAILABLE = -25291
private const val ERR_SEC_INTERACTION_NOT_ALLOWED = -25308

/**
 * iOS tier-2 gate (ADR-0005, ADR-0011): a Keychain item whose access control is
 * `.biometryCurrentSet`, holding random key material.
 *
 * How "anchored to key material, never a boolean" is realized here: the app does NOT ask
 * LocalAuthentication "did this person authenticate?" — that question returns a boolean and a
 * boolean is one patched branch away from always being true. Instead it asks the Keychain for a
 * secret that the Secure Enclave will not release without a live biometric match. Getting the
 * bytes back IS the authentication.
 *
 * `.biometryCurrentSet` (never `.biometryAny`, never `.userPresence`) is contract, not taste: the
 * item is destroyed when the enrolled biometrics change, so a face added by whoever holds the
 * phone cannot inherit the doctor's session. `Scripts/check-biometric-contract.sh` fails the build
 * if that flag drifts.
 *
 * Note on the query shape: `kSecAttrAccessControl` and `kSecAttrAccessible` are mutually exclusive
 * — passing both is `errSecParam` — so the accessibility class travels INSIDE the access control
 * object, where `WhenPasscodeSetThisDeviceOnly` keeps the ADR-0005 floor.
 */
@OptIn(ExperimentalForeignApi::class)
internal class KeychainUnlockGate : UnlockGate {

    override suspend fun enroll(): Boolean {
        if (!biometricsUsable()) return false
        deleteItem()
        val secret = randomBytes(SECRET_BYTES) ?: return false
        val accessControl = SecAccessControlCreateWithFlags(
            null,
            kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly,
            kSecAccessControlBiometryCurrentSet,
            null,
        ) ?: return false

        val status = secret.usePinned { pinned ->
            val data = CFDataCreate(null, pinned.addressOf(0).reinterpret(), secret.size.toLong())
            val service = KEYCHAIN_SERVICE.toCfString()
            val account = UNLOCK_ACCOUNT.toCfString()
            val query = cfDictionary(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to service,
                kSecAttrAccount to account,
                kSecAttrAccessControl to accessControl,
                kSecUseDataProtectionKeychain to kCFBooleanTrue,
                kSecValueData to data,
            )
            val result = SecItemAdd(query, null)
            CFRelease(query)
            CFRelease(account)
            CFRelease(service)
            CFRelease(data)
            result
        }
        CFRelease(accessControl)
        return status == errSecSuccess
    }

    override suspend fun unlock(): UnlockOutcome = memScoped {
        val service = KEYCHAIN_SERVICE.toCfString()
        val account = UNLOCK_ACCOUNT.toCfString()
        val query = cfDictionary(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service,
            kSecAttrAccount to account,
            kSecUseDataProtectionKeychain to kCFBooleanTrue,
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        val out = alloc<CFTypeRefVar>()
        // This call IS the biometric prompt: the Secure Enclave evaluates the ACL before it hands
        // any bytes back. There is no separate "did they authenticate" question to spoof.
        val status = SecItemCopyMatching(query, out.ptr)
        CFRelease(query)
        CFRelease(account)
        CFRelease(service)

        when (status) {
            errSecSuccess -> {
                val data: CFDataRef? = out.value?.reinterpret()
                val length = CFDataGetLength(data).toInt()
                if (data != null) CFRelease(data)
                // Real material of the expected size came back, so the OS authenticated the user.
                if (length == SECRET_BYTES) UnlockOutcome.Unlocked else UnlockOutcome.Failed
            }

            ERR_SEC_USER_CANCELED -> UnlockOutcome.Cancelled

            // The item vanished: `.biometryCurrentSet` destroys it when enrollment changes, which
            // is exactly the event this tier is paid to detect.
            errSecItemNotFound -> UnlockOutcome.Invalidated

            // HONEST AMBIGUITY, declared rather than papered over: iOS reports both "wrong
            // biometric" and "item invalidated by re-enrollment" as errSecAuthFailed, so this app
            // cannot always tell them apart. Both are treated as a failed attempt, and the attempt
            // ceiling ends the session either way — the fail-closed direction is the same.
            ERR_SEC_AUTH_FAILED -> UnlockOutcome.Failed

            ERR_SEC_NOT_AVAILABLE, ERR_SEC_INTERACTION_NOT_ALLOWED -> UnlockOutcome.Unavailable

            else -> UnlockOutcome.Failed
        }
    }

    override suspend fun clear() {
        deleteItem()
    }

    private fun biometricsUsable(): Boolean =
        LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null)

    private fun deleteItem() {
        val service = KEYCHAIN_SERVICE.toCfString()
        val account = UNLOCK_ACCOUNT.toCfString()
        val query = cfDictionary(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service,
            kSecAttrAccount to account,
            kSecUseDataProtectionKeychain to kCFBooleanTrue,
        )
        SecItemDelete(query)
        CFRelease(query)
        CFRelease(account)
        CFRelease(service)
    }

    private fun randomBytes(size: Int): ByteArray? {
        val bytes = ByteArray(size)
        val status = bytes.usePinned { pinned ->
            SecRandomCopyBytes(kSecRandomDefault, size.toULong(), pinned.addressOf(0))
        }
        return if (status == errSecSuccess) bytes else null
    }

    private fun String.toCfString(): CFStringRef? = CFStringCreateWithCString(null, this, kCFStringEncodingUTF8)

    private fun cfDictionary(vararg pairs: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
        val dict = CFDictionaryCreateMutable(
            null,
            pairs.size.toLong(),
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )
        pairs.forEach { (k, v) -> CFDictionaryAddValue(dict, k, v) }
        return dict
    }
}
