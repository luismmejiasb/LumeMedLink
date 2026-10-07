# ADR-0009 — Android tier-1 storage: a Keystore-held AES-GCM key over private files, zero dependencies

- **Status:** Accepted · 2026-08-21 (session proposal under the author's S1.1 authorization;
  supersedes nothing — it closes the choice ADR-0005 explicitly left "decided at wiring time")
- **Related:** ADR-0005 (the two-tier contract this implements); ADR-0003 (what the tier holds).

## Context

ADR-0005 pinned the Android tier-1 CONTRACT (hardware-backed key, `setUnlockedDeviceRequired`,
the `isDeviceSecure` app-level floor, no backup) but deliberately not the storage mechanism,
because the obvious library — Jetpack `EncryptedSharedPreferences` — is deprecated. The wiring
moment is S1.1's session layer, which is now.

## Decision

`KeystoreSecureStore`: one AES-256-GCM key generated INSIDE AndroidKeyStore (non-exportable by
construction), encrypting each value into its own file under `filesDir/lume_secure/`, format
`[ivSize][iv][ciphertext]`, IV randomized by Keystore per encryption.

- **Zero new dependencies** (§8.8): `javax.crypto` + `android.security.keystore`, nothing else.
  No SharedPreferences, no DataStore, no deprecated Jetpack Security.
- **Both floor pieces of ADR-0005**: `setUnlockedDeviceRequired(true)` on the key (API 28+; on
  26/27 the API does not exist and the second piece carries the floor alone — a declared
  degradation, not a hidden one), and `put` refuses with `isDeviceSecure == false`.
- **Tier-1 parameters**: `setUserAuthenticationRequired(false)` — silent refresh must read
  without a prompt. The tier-2 unlock key is a DIFFERENT key whose parameters ADR-0005 already
  pins as contract; it arrives with the shell's biometric gate.
- **Fail closed on decrypt**: a GCM authentication failure loads as `null` (no session), never a
  crash loop — mirroring the iOS store, where removing the passcode deletes the item.
- **Wipe** removes the files AND the Keystore key — the logout contract's disk half.
  *(Corrección 2026-09-21: cierto de `wipe()` desde siempre, y falso del LOGOUT hasta esa fecha —
  ningún camino de logout llamaba a `wipe()`. Ver la enmienda de ADR-0014.)*

## Consequences

- Uninstall destroys key and files (Android's native behavior) — no install sentinel needed on
  this side, as ADR-0005 already declared.
- The store serializes writes behind a mutex; multi-process access is out of contract (this app
  has one process).
- Not covered by automated tests: AndroidKeyStore does not exist on the JVM host-test runner, and
  this repo refuses a Robolectric dependency for it (§8.8). The store is exercised on device when
  the shell lands (S1.2 checklist); the session logic above it is fully tested against fakes.
  Same asymmetry as iOS, where the hostless K/N test runner reaches no keychain (-25291) and the
  roundtrip spec is @Ignore'd until hosted. Both stated in bitácora 0007.

## Amendment, 2026-10-07 — the alias carries the key's provenance (task `0008`)

**The defect.** `obtainKey()` reused the key under its alias without asking how it was made. The generation asks for
`setUnlockedDeviceRequired(true)` only from API 28, so a key made on API 26/27 — with the parameter missing — was
reused forever after an operating-system update. The degradation the code accepts on an old API became permanent on a
new one, and nothing noticed.

**Why the obvious fix is not available.** "Read the key's `KeyInfo` and rotate it if it does not meet today's
parameters" cannot see the one parameter that matters: `KeyInfo` has no accessor for `unlockedDeviceRequired` (checked
against the API 36 `android.jar`). A key's parameters cannot be read back; only its alias can.

**Decision.** The alias is the provenance. On API 28+ the key lives under `lume_session_tier1_udr`, which is created
ONLY with the parameter; `lume_session_tier1` is the API 26/27 alias, and also the one every key made before this
amendment carries, on any API. On API 28+, a key found under the legacy alias is retired together with the ciphertext
it encrypted — files first, then the key — and a new key is made with the parameter. That is a local logout, the same
direction as a device-credential reset; the ciphertext is not re-encrypted, because reading it would give the old key
one more use and the tier-1 tokens can be asked for again. The wipe deletes both aliases.

**Consequences.** Every install that predates this amendment rotates once on API 28+ — today there is none outside the
author's devices, since no login exists. The OS update itself cannot be reproduced on an emulator: the device test
(`Tier1KeyRotationOnDeviceTest`) seeds its RESULT — the legacy alias, made without the parameter, with a value in the
store's own format — and a control first proves the seed is readable. Seen red for each way it can regress: the
retirement deleted, the single alias for every API (the code before this amendment), the key retired with its
ciphertext kept, and the old wipe that deleted only the legacy alias. That last one is only caught because the logout
device tests now assert EVERY tier-1 alias with a precondition that the key in use existed: their old assertion named
`lume_session_tier1`, which after this change never exists on API 28+, and would have passed without looking at the key.
