package com.luismejias.lumemedlink.core.crash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIDevice
import kotlin.experimental.ExperimentalNativeApi

/**
 * The queue's files in Application Support/crash, the directory excluded from backup (ADR-0051 §5), as the install
 * marker's is (ADR-0028). Foundation answers with booleans, so a failed write is silence, as [CrashFileStore] asks.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class ApplicationSupportCrashFileStore : CrashFileStore {
    private val fileManager = NSFileManager.defaultManager

    private fun directory(): String? {
        val support = NSSearchPathForDirectoriesInDomains(
            directory = NSApplicationSupportDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true,
        ).firstOrNull() as? String ?: return null
        val crash = "$support/crash"
        if (!fileManager.fileExistsAtPath(crash)) {
            fileManager.createDirectoryAtPath(
                crash,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
            NSURL.fileURLWithPath(crash).setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
        }
        return crash
    }

    override fun write(name: String, text: String) {
        val path = directory()?.let { "$it/$name" } ?: return
        NSString.create(
            string = text,
        ).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    override fun read(name: String): String? {
        val path = directory()?.let { "$it/$name" } ?: return null
        return NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)
    }

    override fun delete(name: String) {
        val path = directory()?.let { "$it/$name" } ?: return
        fileManager.removeItemAtPath(path, error = null)
    }

    override fun names(): List<String> {
        val dir = directory() ?: return emptyList()
        return fileManager.contentsOfDirectoryAtPath(dir, error = null)?.filterIsInstance<String>().orEmpty()
    }
}

@Composable
internal actual fun rememberCrashFileStore(): CrashFileStore = remember { ApplicationSupportCrashFileStore() }

@Composable
internal actual fun rememberAppIdentity(): AppIdentity = remember {
    val info = NSBundle.mainBundle.infoDictionary
    AppIdentity(
        version = info?.get("CFBundleShortVersionString") as? String ?: "",
        build = info?.get("CFBundleVersion") as? String ?: "",
        platform = CrashPlatform.IOS,
        osVersion = UIDevice.currentDevice.systemVersion,
    )
}

/**
 * Kotlin/Native's frames read like `3 LumeMedLink 0x… kfun:com.luismejias.lumemedlink.app#App(){} + 123`: the class
 * (or the package, for a top-level function) before `#`, the function after it, and no line number. An internal
 * function reads `kfun:pkg.Class.function#internal` instead.
 */
private val KFUN = Regex("""kfun:([A-Za-z0-9_.$]+)#([A-Za-z0-9_<>$-]+)""")

@OptIn(ExperimentalNativeApi::class)
internal actual fun Throwable.platformFrames(): List<CrashFrame> = getStackTrace().mapNotNull { framesOf(it) }

internal fun framesOf(line: String): CrashFrame? = KFUN.find(line)?.let { match ->
    val (owner, after) = match.destructured
    // An internal function is mangled the other way round — `kfun:pkg.Class.function#internal` — measured on the
    // simulator: the function is the last segment before `#`, and «internal» is not its name.
    if (after == "internal" && '.' in owner) {
        CrashFrame(className = owner.substringBeforeLast('.'), functionName = owner.substringAfterLast('.'))
    } else {
        CrashFrame(className = owner, functionName = after)
    }
}

/** Set once, so a second install does not wrap the hook around itself. Process-wide by nature, like the hook. */
@OptIn(ExperimentalNativeApi::class)
internal actual fun installCrashCapture(queue: CrashQueue, identity: AppIdentity, nowEpochMillis: () -> Long) {
    val previous = getUnhandledExceptionHook()
    if (previous is RecordingHook) return
    setUnhandledExceptionHook(RecordingHook(previous, queue, identity, nowEpochMillis))
}

@OptIn(ExperimentalNativeApi::class)
private class RecordingHook(
    private val previous: ReportUnhandledExceptionHook?,
    private val queue: CrashQueue,
    private val identity: AppIdentity,
    private val nowEpochMillis: () -> Long,
) : ReportUnhandledExceptionHook {
    override fun invoke(throwable: Throwable) {
        try {
            val now = nowEpochMillis()
            queue.record(crashReportOf(throwable, throwable.platformFrames(), identity, now), now)
        } finally {
            if (previous != null) previous.invoke(throwable) else terminateWithUnhandledException(throwable)
        }
    }
}
