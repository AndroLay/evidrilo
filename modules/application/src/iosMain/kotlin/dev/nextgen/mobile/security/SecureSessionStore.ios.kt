package dev.nextgen.mobile.security

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

private const val SERVICE = "dev.nextgen.evidrilo.session.v1"
private const val ACCOUNT = "current"

actual fun createSecureSessionStore(): SecureSessionStore = IosSecureSessionStore()

@OptIn(ExperimentalForeignApi::class)
private class IosSecureSessionStore : SecureSessionStore {
    override fun read(): StoredAccountSession? = withBaseQuery(
        kSecReturnData to kCFBooleanTrue,
        kSecMatchLimit to kSecMatchLimitOne,
    ) { query ->
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            when (status) {
                errSecItemNotFound -> null
                errSecSuccess -> {
                    val data = CFBridgingRelease(result.value) as? NSData
                        ?: throw IllegalStateException("Keychain session payload is invalid.")
                    val encoded = data.utf8String()
                    SecureSessionRecordCodec.decode(encoded)
                        ?: throw IllegalStateException("Keychain session payload is invalid.")
                }

                else -> throw IllegalStateException("Keychain session could not be read.")
            }
        }
    }

    override fun write(session: StoredAccountSession) {
        val data = SecureSessionRecordCodec.encode(session).utf8Data()
        val retainedData = CFBridgingRetain(data)
        try {
            val addStatus = withBaseQuery(kSecValueData to retainedData) { query ->
                SecItemAdd(query, null)
            }
            if (addStatus == errSecSuccess) return
            if (addStatus != errSecDuplicateItem) {
                throw IllegalStateException("Keychain session could not be written.")
            }

            val updateStatus = withBaseQuery { query ->
                memScoped {
                    val attributes = cfDictionaryOf(kSecValueData to retainedData)
                    try {
                        SecItemUpdate(query, attributes)
                    } finally {
                        CFBridgingRelease(attributes)
                    }
                }
            }
            check(updateStatus == errSecSuccess) { "Keychain session could not be updated." }
        } finally {
            CFBridgingRelease(retainedData)
        }
    }

    override fun clear() {
        val status = withBaseQuery { query -> SecItemDelete(query) }
        check(status == errSecSuccess || status == errSecItemNotFound) {
            "Keychain session could not be cleared."
        }
    }

    private inline fun <T> withBaseQuery(
        vararg extra: Pair<CFStringRef?, CFTypeRef?>,
        operation: (CFDictionaryRef?) -> T,
    ): T = memScoped {
        val retainedService = CFBridgingRetain(SERVICE)
        val retainedAccount = CFBridgingRetain(ACCOUNT)
        try {
            val query = cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to retainedService,
                kSecAttrAccount to retainedAccount,
                kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                *extra,
            )
            try {
                operation(query)
            } finally {
                CFBridgingRelease(query)
            }
        } finally {
            CFBridgingRelease(retainedService)
            CFBridgingRelease(retainedAccount)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.cfDictionaryOf(
    vararg items: Pair<CFStringRef?, CFTypeRef?>,
): CFDictionaryRef? {
    val map = mapOf(*items)
    val keys = allocArrayOf(*map.keys.toTypedArray())
    val values = allocArrayOf(*map.values.toTypedArray())
    return CFDictionaryCreate(
        kCFAllocatorDefault,
        keys.reinterpret(),
        values.reinterpret(),
        map.size.convert(),
        null,
        null,
    )
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun String.utf8Data(): NSData {
    val bytes = encodeToByteArray()
    return bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.utf8String(): String {
    val rawBytes = bytes?.reinterpret<kotlinx.cinterop.ByteVar>()
        ?: throw IllegalStateException("Keychain session text is invalid.")
    return ByteArray(length.toInt()) { index -> rawBytes[index] }.decodeToString()
}
