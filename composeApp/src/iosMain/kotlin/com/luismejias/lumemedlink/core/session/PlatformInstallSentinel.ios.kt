package com.luismejias.lumemedlink.core.session

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileProtectionKey
import platform.Foundation.NSFileProtectionNone
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/** Empty by design: the file's EXISTENCE is the whole datum, so it carries nothing to protect. */
private const val MARKER_NAME = "install.sentinel"

/**
 * The iOS half of F7, and the only one that does any work (ADR-0028).
 *
 * A file in **Application Support**, which the container owns and deleting the app destroys, while
 * the Keychain survives. That asymmetry is the entire mechanism: the marker's absence means "this
 * container is new", and a new container next to surviving Keychain items means inherited secrets.
 *
 * ## Three properties of the file, each load-bearing
 *
 * **No data protection** (`NSFileProtectionNone`). This is a non-sensitive boolean, and it must be
 * readable **before the device's first unlock**. Otherwise a background launch on a locked phone
 * cannot read the marker, concludes "fresh install", and purges a perfectly valid session — the
 * documented mass-logout failure mode of this pattern, and the reason a plain "protect everything"
 * default would be a bug here rather than caution.
 *
 * **Excluded from backup.** A restore lands with the Keychain repopulated but without the marker,
 * so the guard fires and purges the restored residue too. Stricter than not excluding it, and it
 * follows §8.5's exclusion rule.
 *
 * **Application Support, not Documents or Caches.** Caches can be evicted by the system under disk
 * pressure — an evicted marker reads as a fresh install and logs the doctor out for no reason.
 * Documents is user-visible surface this app has no business writing to. Application Support is
 * the container directory for exactly this, and iOS does not create it, so it is created here.
 */
@OptIn(ExperimentalForeignApi::class)
private object KeychainInstallSentinel : InstallSentinel {

    private val fileManager = NSFileManager.defaultManager

    private fun markerPath(): String? {
        val support = NSSearchPathForDirectoriesInDomains(
            directory = NSApplicationSupportDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true,
        ).firstOrNull() as? String ?: return null
        return "$support/$MARKER_NAME"
    }

    override suspend fun hasRunBefore(): Boolean {
        val path = markerPath() ?: return false
        return fileManager.fileExistsAtPath(path)
    }

    override suspend fun markHasRun() {
        val path = markerPath() ?: error("No Application Support directory: cannot mark the install.")
        val support = path.substringBeforeLast('/')

        // iOS does not create Application Support. Doing it here rather than assuming it exists is
        // the difference between a marker that works on the first launch and one that never writes.
        if (!fileManager.fileExistsAtPath(support)) {
            fileManager.createDirectoryAtPath(
                path = support,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }

        val created = fileManager.createFileAtPath(
            path = path,
            contents = NSData(),
            attributes = mapOf<Any?, Any?>(NSFileProtectionKey to NSFileProtectionNone),
        )
        check(created) { "Could not write the install marker; the container stays unmarked on purpose." }

        // Best effort by nature: if this fails the marker still works, it just also gets backed up,
        // and a restore would then skip the purge. Reported as a failure so the container stays
        // unmarked and the next launch tries again, rather than silently shipping the weaker
        // guarantee.
        val excluded = NSURL.fileURLWithPath(path).setResourceValue(
            value = true,
            forKey = NSURLIsExcludedFromBackupKey,
            error = null,
        )
        if (!excluded) {
            // THE MARKER COMES BACK OFF. ADR-0028 says a failure leaves the container UNMARKED, and
            // until 2026-09-21 this path broke that promise in the worst possible way: the file was
            // already written, so `markHasRun` threw, `enforceInstallBoundary` answered FAILED — and
            // the next launch read a marker that said "this container has run before" and skipped
            // the purge. Worse still, the marker it found was the one WITHOUT the backup exclusion,
            // which is exactly the residue a restore was supposed to purge. A half-written guard
            // that reports failure and leaves its own evidence behind is not a guard.
            fileManager.removeItemAtPath(path, error = null)
            error("Could not exclude the install marker from backup; the marker was removed.")
        }
    }
}

internal actual fun platformInstallSentinel(): InstallSentinel = KeychainInstallSentinel
