package com.luismejias.lumemedlink.core.crash

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.IOException

/**
 * The queue's files in `noBackupFilesDir`: the app's own directory that the platform leaves out of the cloud backup
 * and of the device-to-device transfer (ADR-0051 §5). Each file errors only into silence — see [CrashFileStore].
 */
private class NoBackupCrashFileStore(context: Context) : CrashFileStore {
    private val directory = File(context.noBackupFilesDir, "crash")

    @Suppress("SwallowedException")
    override fun write(name: String, text: String) {
        try {
            directory.mkdirs()
            File(directory, name).writeText(text)
        } catch (failed: IOException) {
            // A full or unwritable disk: the crash goes unrecorded, and the platform still sees it.
        } catch (refused: SecurityException) {
            // Same.
        }
    }

    @Suppress("SwallowedException")
    override fun read(name: String): String? = try {
        File(directory, name).takeIf { it.isFile }?.readText()
    } catch (failed: IOException) {
        null
    }

    override fun delete(name: String) {
        File(directory, name).delete()
    }

    override fun names(): List<String> = directory.list()?.toList().orEmpty()
}

@Composable
internal actual fun rememberCrashFileStore(): CrashFileStore {
    val context = LocalContext.current.applicationContext
    return remember { NoBackupCrashFileStore(context) }
}

@Composable
internal actual fun rememberAppIdentity(): AppIdentity {
    val context = LocalContext.current.applicationContext
    return remember {
        val info = packageInfo(context)
        AppIdentity(
            version = info.versionName.orEmpty(),
            build = versionCode(info).toString(),
            platform = CrashPlatform.ANDROID,
            osVersion = Build.VERSION.RELEASE.orEmpty(),
        )
    }
}

// minSdk is 26: the flags overload starts at 33 and longVersionCode at 28, so the older calls stay for older systems.
@Suppress("DEPRECATION")
private fun packageInfo(context: Context): PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
} else {
    context.packageManager.getPackageInfo(context.packageName, 0)
}

@Suppress("DEPRECATION")
private fun versionCode(info: PackageInfo): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

internal actual fun Throwable.platformFrames(): List<CrashFrame> =
    stackTrace.map { CrashFrame(it.className, it.methodName, it.lineNumber.takeIf { line -> line > 0 }) }

/** Records, then hands the crash to whatever handled it before — the system's, which ends the process. */
private class RecordingHandler(
    val previous: Thread.UncaughtExceptionHandler?,
    private val queue: CrashQueue,
    private val identity: AppIdentity,
    private val nowEpochMillis: () -> Long,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val now = nowEpochMillis()
            queue.record(crashReportOf(throwable, throwable.platformFrames(), identity, now), now)
        } finally {
            previous?.uncaughtException(thread, throwable)
        }
    }
}

internal actual fun installCrashCapture(queue: CrashQueue, identity: AppIdentity, nowEpochMillis: () -> Long) {
    val current = Thread.getDefaultUncaughtExceptionHandler()
    if (current is RecordingHandler) return
    Thread.setDefaultUncaughtExceptionHandler(RecordingHandler(current, queue, identity, nowEpochMillis))
}
