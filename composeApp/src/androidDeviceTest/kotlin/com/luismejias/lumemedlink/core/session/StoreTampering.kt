package com.luismejias.lumemedlink.core.session

import android.content.Context
import java.io.File

/**
 * Flips one byte of a stored entry's ciphertext, so its GCM tag no longer closes — the signature of a
 * tampered file (task 0003). Test-only, and in `core/session` because raw writes to the store's
 * directory belong here and nowhere else (check-forbidden-patterns P4, ADR-0022).
 */
internal fun tamperWithStoredEntry(context: Context, key: SecureStoreKey) {
    val file = File(File(context.filesDir, "lume_secure"), "${key.storageKey}.bin")
    val blob = file.readBytes()
    blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 0x01).toByte()
    file.writeBytes(blob)
}
