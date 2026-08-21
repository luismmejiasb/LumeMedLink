# ADR-0022 — Any bytes at rest go through the secure store

- **Status:** Accepted · 2026-08-21 (fortification slice F8)
- **Related:** ADR-0009 (the Android store), ADR-0014 (the logout contract), ADR-0015 (backup and
  transfer exclusion), ADR-0016 (the iOS URL cache that escaped all three).

## Context

This app is online-only and has no cache. That is exactly when this is cheap to decide: the first
slice that wants to hold an agenda offline will otherwise invent a second storage path, and a second
path has to re-earn three properties it will silently fail to earn.

We already watched that happen. The iOS URL cache (ADR-0016) was a second storage path nobody chose:
it wrote response bodies **and bearer tokens** to `Library/Caches`, unencrypted, outside the logout
wipe, because the logout contract enumerates `SecureStoreKey` and knew nothing about it.

## Decision

**Anything this app keeps at rest goes through `core/session`'s `SecureStore`.** Not a convention —
`Scripts/check-forbidden-patterns.sh` rule P4 fails the build on a file write outside `core/`.

The reason is that the store already carries, and a new path would not:

1. **Encryption** under a key that never leaves the device (ADR-0009 on Android, Keychain on iOS).
2. **Exclusion from backup and device transfer** — its directory is inside the app's private data,
   which ADR-0015's rules exclude in both destinations.
3. **Erasure by logout**, verified against real hardware (ADR-0014), including deletion of the key
   itself so a stray copy is permanently undecryptable.

A cache that reuses it inherits all three for free. A cache that writes its own file inherits none,
and nothing would notice.

## Consequences

- The first caching slice designs a **key namespace**, not a storage mechanism, and adding its key
  to the `SecureStoreKey` enum puts it under the wipe test automatically (ADR-0014).
- **`isExcludedFromBackup` on iOS lands with that slice, not before.** There is no file to flag
  today — the app persists only Keychain items — and F6 corrected two documents that named it as a
  live control when it was not.
- Remote image bytes (a profile photo) are covered by the same rule: they arrive through the stack
  and, if they are ever kept, through the store. The image-loader denylist (ADR-0018) stops the
  usual shortcut, which would bring its own cache and its own network path.
- **Not covered:** memory. A cache held only in RAM is outside this rule and outside the wipe test;
  when one appears it needs its own clearing on logout, and this ADR does not pretend otherwise.
