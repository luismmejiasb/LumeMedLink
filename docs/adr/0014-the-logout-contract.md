# ADR-0014 — The logout contract: what it erases, and the one thing it cannot

- **Status:** Accepted · 2026-08-21 (fortification slice F5)
- **Related:** §8.13 (logout = hard wipe) and §8.7 (T16 vocabulary) of this constitution; threat
  model T3 (later persistence); ADR-0005 (the tiers being erased); ADR-0011 (tier-2 material).

## Context

If closing the session does not truly erase the token, the next person holding the phone is inside
without credentials — and "truly" is doing a lot of work in that sentence. A logout that clears
memory but leaves ciphertext on disk is recoverable by a forensic dump; one that deletes files but
keeps the Keystore key alive leaves any stray copy still decryptable.

Until this slice, every test of `core/session` ran against a **fake in-memory map**. The contract
was proven as logic and merely asserted as behaviour — a fake cannot tell you whether
AndroidKeyStore persisted anything, whether the file left the disk, or whether the key was really
deleted (bitácora 0007 declared this gap; this ADR closes it).

## Decision

**Logout erases, in one call, everything a session leaves behind:**

1. **Memory** — the in-process token pair.
2. **Disk** — the ciphertext files, *deleted*, not merely overwritten or orphaned.
3. **The Keystore key itself** — so that if any copy of the ciphertext survives anywhere (a
   backup that predates `allowBackup=false`, a forensic image), it is permanently undecryptable.
   Deleting the key is what turns "erased" into "unrecoverable".
4. **The tier-2 unlock material** — the biometric key and its challenge (ADR-0011).
5. **The lock state** — the next session starts born-locked.

**The set of secrets is enumerable by construction.** `SecureStoreKey` is an enum, not loose
constants, and `SecureStoreWipeTest` iterates it: a secret added by a future slice is covered by
the wipe test the moment it is declared. Nobody has to remember to extend that test, and
remembering is exactly what fails.

## What logout does NOT do, said plainly

**This is a LOCAL logout. It does not revoke anything server-side.** The app has no revocation
call wired, and a refresh token exfiltrated *before* logout would remain valid to the backend until
it expires. Two consequences, neither of them cosmetic:

- **The UI may never say "cerraste sesión en todos tus dispositivos"**, or anything implying remote
  effect. This is the same vocabulary discipline as T16 (a local purge is not the right of
  erasure): the app says what it did, which is "cerrar sesión en este dispositivo".
- **Whether the contract offers revocation is an open question**, not an assumption. It gets
  confirmed when the auth flow is wired (F10/F11); if the platform has no such operation, it
  becomes a backend request rather than a silently accepted gap.

## Consequences

- Verified on a real device, not asserted: `LogoutWipeOnDeviceTest` (4 tests, green) exercises the
  actual AndroidKeyStore-backed store — round trip, **the plaintext is not on disk**, logout leaves
  nothing readable, and the wipe removes both the files and the key.
- **iOS is unverified**, as everywhere else in this repo: the hostless Kotlin/Native runner reaches
  no keychain, so the iOS store's wipe is proven by code review and the shared contract tests
  only. It gets its device proof when the iOS host exists.
- Because the wipe clears a whole namespace rather than a list of keys, a forgotten key cannot
  survive it — the enum exists for the *test*, not to drive the erase.
