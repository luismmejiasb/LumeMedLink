package com.luismejias.lumemedlink.core.crash

import androidx.compose.runtime.Composable

/**
 * The platform's file store for the queue: Android's no-backup directory, iOS's Application Support excluded from
 * backup (ADR-0051 §5). @Composable because Android needs a Context, as `rememberSecureStore` does.
 */
@Composable
internal expect fun rememberCrashFileStore(): CrashFileStore

/** The build and system this app runs on, for every report. */
@Composable
internal expect fun rememberAppIdentity(): AppIdentity

/** The platform's raw frames for [throwable], outermost last; [crashReportOf] keeps only this app's. */
internal expect fun Throwable.platformFrames(): List<CrashFrame>

/**
 * Catches what the process does not (ADR-0051 §6): Android's uncaught JVM exceptions, iOS's uncaught Kotlin ones. It
 * writes the report and hands control back to the platform in a `finally`, so a failure while writing can never stand
 * in for the first crash (§7). Idempotent: installing twice does not chain the handler to itself.
 */
internal expect fun installCrashCapture(queue: CrashQueue, identity: AppIdentity, nowEpochMillis: () -> Long)
