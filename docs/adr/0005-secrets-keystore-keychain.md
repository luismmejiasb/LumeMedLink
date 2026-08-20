# ADR-0005 — Secrets: hardware-backed stores on both platforms, two tiers, no backup

- **Status:** Accepted · 2026-08-17
- **Related:** ADR-0005 de LumeMed (the two-tier design being mirrored); §8.4/§8.14 of this
  constitution.

## Context

LumeMed learned this the expensive way (its 2026-08-14 audit): declaring tiers is not wiring them.
This repo starts with the corrected doctrine.

## Decision

Two tiers, on each platform's hardware-backed store:

| Tier | iOS | Android | Holds |
| --- | --- | --- | --- |
| **Tokens** (silent refresh must read without a prompt) | Keychain `whenPasscodeSetThisDeviceOnly` (`ThisDeviceOnly` always; simulator deviation compiled out, as LumeMed does) | Keystore-wrapped storage, `setUnlockedDeviceRequired(true)` | access + refresh token |
| **Unlock** (the biometric gate reads key material, never a boolean) | `SecAccessControl(.biometryCurrentSet)` | Keystore key with `setUserAuthenticationRequired(true)` + `setInvalidatedByBiometricEnrollment(true)` | the re-auth secret |

- Enrolling a new face/finger **invalidates** the unlock tier on both platforms — that is the
  property the tier is paid for, and both OSes provide it natively.
- **Never** `SharedPreferences`/`DataStore`/`NSUserDefaults` for a secret. Jetpack
  `EncryptedSharedPreferences` is deprecated and is NOT the assumed Android implementation; the
  concrete wrapper is decided at wiring time against the Keystore directly.
- **No backup, stated three times**: Android `allowBackup=false` + `dataExtractionRules` excluding
  everything; Keystore keys are non-exportable by construction; iOS items are `ThisDeviceOnly`.

## The asymmetry, declared (install sentinel)

iOS Keychain **survives uninstall** — the mirror of LumeMed's ADR-0037 (fresh-install sentinel that
purges inherited secrets) applies whole on the iOS side. Android wipes app data and Keystore entries
on uninstall — the sentinel is unnecessary there. One rule, one platform; writing it down is what
keeps someone from "porting" it to Android as cargo cult or deleting it from iOS as dead code.
