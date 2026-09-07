package com.luismejias.lumemedlink.core.session

/**
 * On Android there is nothing to guard against, and this file exists to say so rather than to leave
 * a silence somebody later reads as an oversight (F7, ADR-0028).
 *
 * **Why the platform already gives the guarantee.** Uninstalling an Android app removes its data
 * directory *and* its Keystore entries — both are scoped to the app's UID, and the system reclaims
 * the UID. There is no equivalent of iOS's Keychain outliving the container, so a reinstall starts
 * with nothing to inherit. This is §8.14's declared asymmetry: an entire rule that only one
 * platform needs.
 *
 * **And the two ways secrets could come back are closed elsewhere, by controls that a marker could
 * not replace anyway.** Cloud backup and device-to-device transfer are both refused by F6
 * (`allowBackup=false` *and* `dataExtractionRules`, because at targetSdk ≥ 31 the first is
 * deliberately ignored for D2D). Worth following the logic: if backup were ever re-enabled, the
 * marker file would be backed up *with* the rest, so a restore would land already marked and this
 * sentinel would skip the purge. A marker cannot defend the case F6 defends — which is the real
 * reason it is not worth having here, rather than mere redundancy.
 *
 * So this reports "already established", the branch that touches nothing. Reporting a fresh install
 * would wipe a valid session on every launch.
 */
private object AndroidInstallSentinel : InstallSentinel {
    override suspend fun hasRunBefore(): Boolean = true

    override suspend fun markHasRun() = Unit
}

internal actual fun platformInstallSentinel(): InstallSentinel = AndroidInstallSentinel
