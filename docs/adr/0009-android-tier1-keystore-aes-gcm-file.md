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
