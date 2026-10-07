# ADR-0035 — An unreadable secure-store entry throws; it never reads as "never written"

- **Status**: Accepted (the author chose this option on 2026-10-07, over a typed result and over leaving it)
- **Date**: 2026-10-07
- **Related**: ADR-0025 (`probeSession` turns a store that cannot answer into a sign-in screen and a
  security event), ADR-0009 (the Android tier-1 store), ADR-0023 (the security event channel), ADR-0034
  (the attempt ledger), task `0003`.

## Context

`KeystoreSecureStore.get()` returned `null` both when an entry did not exist and when it existed and could
not be read — a GCM tag that does not close, which is the signature of a tampered file, a truncated blob,
a key that is gone. A manipulated store therefore looked exactly like a first launch:
`SECURE_STORE_UNREADABLE`, the signal §8.16 exists to carry, was never produced on Android. The iOS
store already threw on any unexpected status, and ADR-0025 had built the place that decides what that means.

## Decision

1. **`SecureStore.get()` returns `null` only for "nothing was ever written"**, and throws
   `SecureStoreUnreadableException` for an entry that exists and cannot be read. The exception carries no
   cause and no platform text (§8.1), and is not an `IllegalStateException` — that is
   `CancellationException`'s supertype.
2. **Every caller decides, and every caller fails closed:**
   - `probeSession` (via `TokenStore`): sign-in screen, `SECURE_STORE_UNREADABLE` logged and reported —
     unchanged code, ADR-0025.
   - `FailedAttemptLedger` (via `SessionLock`): the session ends, `ATTEMPTS_UNRECORDABLE` (ADR-0034).
   - The Android tier-2 challenge (via `SessionLock`): the session ends with a new reason,
     `UNLOCK_MATERIAL_UNREADABLE`. `SessionLock` now catches any non-cancellation failure of the gate;
     until this ADR such an exception escaped into the shell's coroutine.
3. **A file retired with its key is not unreadable.** Obtaining the tier-1 key may retire one made without
   `unlockedDeviceRequired` and delete what it encrypted (ADR-0009, amended the same day); the key is
   obtained first, and a file that retirement removed reads as never written.

## Rejected

- **A typed result** (absent / present / unreadable): the compiler would force every caller to decide, at
  the cost of changing the interface and every test double. The callers are three and each now decides.
- **Leaving it**: fail closed, but silent — the defect this ADR exists for.

## Consequences

- Verified on the emulator by `UnreadableStoreOnDeviceTest`: one byte of the ciphertext flipped, and the
  read throws, `probeSession` answers no session, and the event and the log line are produced. Seen red
  with `null` put back.
- The unlock-path event is not reported yet: `SessionLock` has no reporter (task `0018`).
- An entry that stays unreadable is reported on every launch until a sign-in overwrites it. That is the
  honest count of a condition that persists.
