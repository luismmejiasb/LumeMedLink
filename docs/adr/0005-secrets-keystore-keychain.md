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
| **Tokens** (silent refresh must read without a prompt) | Keychain `whenPasscodeSetThisDeviceOnly` (`ThisDeviceOnly` always; simulator deviation compiled out, as LumeMed does) | Keystore key + `setUnlockedDeviceRequired(true)` **plus an explicit `KeyguardManager.isDeviceSecure` check at session establish** — see the floor note below | access + refresh token |
| **Unlock** (the biometric gate reads key material, never a boolean) | `SecAccessControl(.biometryCurrentSet)` | Keystore key with `setUserAuthenticationRequired(true)`, **auth-per-use** (`setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)`) + `setInvalidatedByBiometricEnrollment(true)` | the re-auth secret |

**The Android floor is two pieces, not one — stated because they are NOT equivalent to iOS's.**
`whenPasscodeSetThisDeviceOnly` fails closed on a device with no passcode: the write refuses.
`setUnlockedDeviceRequired(true)` does **not** — it only blocks key use while the device is locked,
and a phone with no PIN/pattern is never "locked", so the key would be usable always. The mirror of
the fail-closed property is therefore an **app-level check**: at session establish, refuse to store
tokens when `KeyguardManager.isDeviceSecure == false` — a clinical-adjacent phone with no lock
screen never comes to hold a session, which is the property the iOS class buys by itself.

**And the unlock tier's invalidation is conditional, so the conditions are contract.**
`setInvalidatedByBiometricEnrollment` only bites for keys requiring authentication **per use** with
`BIOMETRIC_STRONG`; a validity-timeout key or a `DEVICE_CREDENTIAL` fallback silently loses the
invalidation-on-new-enrollment property this tier exists for. The parameters in the table are not an
implementation choice — changing them changes the security claim, and needs this ADR reopened.

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
