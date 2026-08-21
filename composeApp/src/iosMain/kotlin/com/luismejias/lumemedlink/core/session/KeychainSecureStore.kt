package com.luismejias.lumemedlink.core.session

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
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
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecUseDataProtectionKeychain
import platform.Security.kSecValueData

/** One service for the whole app: [wipe] deletes exactly this and nothing else (§8.13). */
private const val KEYCHAIN_SERVICE = "com.luismejias.lumemedlink.session"

/**
 * Tier-1 store on the iOS Keychain (ADR-0005): `kSecClassGenericPassword`, accessibility pinned
 * per target by [tokenTierKeychainAccessibility], `ThisDeviceOnly` always, and NEVER
 * synchronizable — the key is not even present (with a ThisDeviceOnly class the combination is
 * an errSecParam, and a sync'd secret would ride iCloud Keychain to other devices).
 *
 * Writes are delete-then-add ON PURPOSE: one code path that also renews attributes. Valid only
 * because every write serializes behind [mutex] — the same guarantee LumeMed's wrapper gets from
 * its actor (keychain-facts: SecItemUpdate cannot change an item's access control anyway).
 *
 * `errSecItemNotFound` (-25300) on read is a `null`, not an error.
 *
 * Every query pins `kSecUseDataProtectionKeychain` — the modern keychain, the only correct one
 * for an iOS app — so device and simulator run one code path. Known limit, stated: the K/N test
 * runner spawns a HOSTLESS process on the simulator and securityd grants it no keychain at all
 * (every call answers -25291 errSecNotAvailable, DP keychain included), so the roundtrip test is
 * @Ignore'd as an executable spec until the shell provides a host (S1.2 verifies on device).
 */
@OptIn(ExperimentalForeignApi::class)
internal class KeychainSecureStore : SecureStore {
    private val mutex = Mutex()

    override suspend fun put(key: String, value: String): Unit = mutex.withLock {
        deleteMatching(key)
        val bytes = value.encodeToByteArray()
        val status = bytes.usePinned { pinned ->
            val data = CFDataCreate(
                null,
                if (bytes.isEmpty()) null else pinned.addressOf(0).reinterpret(),
                bytes.size.toLong(),
            )
            val account = key.toCfString()
            val service = KEYCHAIN_SERVICE.toCfString()
            val query = cfDictionary(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to service,
                kSecAttrAccount to account,
                kSecAttrAccessible to tokenTierKeychainAccessibility,
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
        check(status == errSecSuccess) {
            // The status alone: never the value, never the key's content (§8.1).
            "Keychain add failed with OSStatus $status"
        }
    }

    override suspend fun get(key: String): String? = mutex.withLock {
        memScoped {
            val account = key.toCfString()
            val service = KEYCHAIN_SERVICE.toCfString()
            val query = cfDictionary(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to service,
                kSecAttrAccount to account,
                kSecUseDataProtectionKeychain to kCFBooleanTrue,
                kSecReturnData to kCFBooleanTrue,
                kSecMatchLimit to kSecMatchLimitOne,
            )
            val out = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, out.ptr)
            CFRelease(query)
            CFRelease(account)
            CFRelease(service)
            when (status) {
                errSecSuccess -> {
                    val data: CFDataRef? = out.value?.reinterpret()
                    val length = CFDataGetLength(data).toInt()
                    val text = if (length == 0) {
                        ""
                    } else {
                        CFDataGetBytePtr(data)!!.readBytes(length).decodeToString()
                    }
                    if (data != null) CFRelease(data)
                    text
                }
                errSecItemNotFound -> null
                else -> error("Keychain read failed with OSStatus $status")
            }
        }
    }

    override suspend fun remove(key: String): Unit = mutex.withLock {
        deleteMatching(key)
    }

    override suspend fun wipe(): Unit = mutex.withLock {
        // The OWN service only — never a sweep of the five kSecClass, never SynchronizableAny
        // (both would reach beyond this app's secrets; keychain-facts).
        deleteMatching(account = null)
    }

    /** Delete every item matching the service (+ account when given). NotFound is success. */
    private fun deleteMatching(account: String? = null) {
        val service = KEYCHAIN_SERVICE.toCfString()
        val accountCf = account?.toCfString()
        val pairs = buildList {
            add(kSecClass to (kSecClassGenericPassword as CFTypeRef?))
            add(kSecAttrService to (service as CFTypeRef?))
            add(kSecUseDataProtectionKeychain to (kCFBooleanTrue as CFTypeRef?))
            if (accountCf != null) add(kSecAttrAccount to (accountCf as CFTypeRef?))
        }
        val query = cfDictionary(*pairs.toTypedArray())
        val status = SecItemDelete(query)
        CFRelease(query)
        CFRelease(service)
        if (accountCf != null) CFRelease(accountCf)
        check(status == errSecSuccess || status == errSecItemNotFound) {
            "Keychain delete failed with OSStatus $status"
        }
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
