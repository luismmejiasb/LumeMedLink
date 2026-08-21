# ADR-0015 — Backup and transfer: two attributes, because one does not cover the phone-to-phone path

- **Status:** Accepted · 2026-08-21 (fortification slice F6)
- **Related:** §8.5 of this constitution; threat model T3/T4; ADR-0009 (what is on disk);
  ADR-0014 (the logout contract this complements).

## Context

The threat is a restore on someone else's hardware: a Google backup, or a phone-to-phone migration,
re-materializing this app's data on a device its owner does not control. The repo has carried
`android:allowBackup="false"` since S0.1 and its documents presented that as the defense.

**It was not sufficient, and this was measured rather than reasoned.** On `targetSdk >= 31` the
platform deliberately ignores `allowBackup` for device-to-device migration, gated on the compat
change `IGNORE_ALLOW_BACKUP_IN_D2D` (id 183147249, `enableSinceTargetSdk=31`, confirmed live via
`dumpsys platform_compat`). This app targets 36.

On a booted API 37 emulator, package-scoped and in the same session:

| Configuration | D2D transport result for `com.luismejias.lumemedlink` |
| --- | --- |
| `allowBackup="false"` only (the state before this ADR) | `progress: 512/1024 … 3072/1024`, **`result: Success`** — data extracted |
| plus `dataExtractionRules` with `<device-transfer>` | no progress lines, transport collects nothing |

The cloud transport refused the package in both configurations, with a positive control
(`com.android.providers.settings`) succeeding on each transport in the same session.

## Decision

**Both attributes, always, and they are complementary rather than redundant.**

1. `android:allowBackup="false"` closes the cloud path.
2. `android:dataExtractionRules="@xml/data_extraction_rules"` closes the device-transfer path.

The rules file excludes **all nine domains in both sections** — `root`, `file`, `database`,
`sharedpref`, `external` and their `device_*` twins. All nine are required: the framework walks
nine separate trees, so excluding `root` does **not** cover `file`, `database` or `sharedpref`.

**`<cross-platform-transfer>` is never written.** That section is opt-IN — its presence is what
*enables* Android↔iOS app-data export. An investigation recommended adding it "for symmetry", which
would have opened a new path; the gate now refuses it.

**A rules file that omits `<device-transfer>` is worse than no rules file at all** — the framework
then marks the new scheme in use with an empty, transfer-everything exclude set. Measured: with the
section removed the same package leaked 3072 bytes over D2D, exactly as with no rules file. The
gate treats its absence as a hard failure, not a warning.

**iOS gets no `isExcludedFromBackup` today, because there is no file to flag.** The app persists
only Keychain items, and the device build pins
`kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly` — the one class Apple documents as absent from
both iCloud and encrypted local backups. Naming `isExcludedFromBackup` as a live control would have
been claiming something that does not exist; the constitution and the threat model were corrected
accordingly. It lands with the first cached file (F8).

## Verification

`Scripts/check-backup-posture.sh` gates the source, and `Scripts/verify-no-backup.sh` proves the
behaviour on a device with a positive control on each transport. The latter has a
`--with-live-control` mode that removes `<device-transfer>`, rebuilds, and requires the leak to
reappear — so the negative result is evidence rather than an artifact, and the check is proven able
to see a leak at the moment it claims there is none.

## What is NOT verified, and must not be claimed

- **No real two-phone migration was performed.** All D2D evidence is the `bmgr` transport plus the
  framework's own parse tally. The compat change is `@Overridable` and Google's documentation hedges
  with "on devices from some device manufacturers".
- **API 26–30 behaviour is inferred**, not measured: the only system image on this machine is
  android-37. The reasoning is that the compat change starts at 31, so below it `allowBackup="false"`
  is a complete stop — reasoning, not a run.
- **That no bytes reached a transport's store** was never observed directly (`adb root` is refused
  on this image). What is observable is the framework's and the transport's verdicts.
- **Restore-side behaviour**, the real Google cloud transport with a signed-in account, and OEM
  migration tools (Smart Switch, Mi Mover, Clone Phone) — untested. OEM tools plausibly sit outside
  the BackupManager path entirely, in which case nothing in this ADR governs them.

## Residual risks, recorded rather than closed

- OEM transfer agents, per above.
- Filenames (`session_tokens_v1.bin`) disclose that a session existed, even though the ciphertext is
  inert without the Keystore key. Low severity, previously an unexamined default.
- Debug builds are debuggable by AGP default and reachable by `run-as`/`adb pull`; no gate asserts a
  non-debuggable release. Registered for F21.
- The exclusions stop data *travelling*; they do not make plaintext on disk safe. The invariant that
  actually protects a future agenda cache is ADR-0009's: every persisted byte is encrypted under a
  key that never leaves the device.
