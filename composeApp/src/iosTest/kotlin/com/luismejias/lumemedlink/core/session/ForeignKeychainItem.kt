package com.luismejias.lumemedlink.core.session

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ptr
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecUseDataProtectionKeychain

/**
 * A generic-password item under a service that is NOT this app's — what another component sharing the
 * keychain group would own. Raw SecItem on purpose: going through [KeychainSecureStore] would only ever
 * reach its own service. No value: existence is all the wipe test needs, and synthetic data stays
 * synthetic (§9).
 */
@OptIn(ExperimentalForeignApi::class)
internal class ForeignKeychainItem {
    fun add() {
        delete()
        withQuery { query -> check(SecItemAdd(query, null) == errSecSuccess) { "seeding the foreign item failed" } }
    }

    fun exists(): Boolean = withQuery { query -> SecItemCopyMatching(query, null) == errSecSuccess }

    fun delete() {
        withQuery { query -> SecItemDelete(query) }
    }

    private fun <T> withQuery(use: (CFDictionaryRef?) -> T): T {
        val service = cf("com.luismejias.lumemedlink.test-foreign-service")
        val account = cf("synthetic-foreign-account")
        val query =
            CFDictionaryCreateMutable(null, 4, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        listOf<Pair<CFStringRef?, CFTypeRef?>>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service,
            kSecAttrAccount to account,
            kSecUseDataProtectionKeychain to kCFBooleanTrue,
        ).forEach { (k, v) -> CFDictionaryAddValue(query, k, v) }
        return try {
            use(query)
        } finally {
            CFRelease(query)
            CFRelease(service)
            CFRelease(account)
        }
    }

    private fun cf(text: String): CFStringRef? = CFStringCreateWithCString(null, text, kCFStringEncodingUTF8)
}
