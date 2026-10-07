# ADR-0037 — A session begins on an empty slate, with its tier-2 material, or not at all

- **Status**: Accepted (2026-10-07). Derived from decisions already in force — ADR-0011 (re-entry anchored
  to key material made at a fresh authentication), `SessionLock`'s existing `BIOMETRICS_UNAVAILABLE`
  direction, and ADR-0036 point 1 — not a new direction of failure; the author may reopen it.
- **Date**: 2026-10-07
- **Related**: ADR-0011, ADR-0014 (the logout contract this mirrors), ADR-0022 (the first cache), ADR-0036,
  tasks `0005` and `0020`.

## Context

Two defects from the completeness audits, with one cause — nothing owned the moment a session begins:

1. **`UnlockGate.enroll()` had no caller** (task `0005`). The tier-2 gate is built, measured on devices,
   its invalidation proven — and the key it signs with was never made. The first lock would have asked
   the gate to sign with nothing, read as `Unavailable` or `Invalidated`, and ended every session.
2. **`SessionManager.establish()` wrote over whatever was there** (task `0020`). Everything this app keeps
   lives under fixed names in one namespace, and a rejected refresh erases only the token entry
   (ADR-0036 point 1) — so remains are the normal case. Harmless today; the day a cache exists
   (ADR-0022), account B reads account A's agenda on the same phone. And the shell was never told a
   refresh had been rejected.

## Decision

1. **One function begins a session: `establishSession()`** (`core/session/SessionEntry.kt`), the mirror of
   `performLogout`. In order:
   1. erase the namespace and the previous tier-2 key — or refuse (`SLATE_NOT_CLEARED`);
   2. make the new tier-2 key — or refuse (`TIER2_UNAVAILABLE`), with nothing written;
   3. write the tokens and open the window — or erase it all again through the logout contract and refuse
      (`SESSION_NOT_SAVED`).
2. **Erasing at the beginning, not keys per account.** Every future cache is then safe without its author
   remembering anything; per-account keys would have to be chosen right in every slice that stores.
3. **No biometrics, no session.** The lock already ends a session whose biometrics are unavailable; a
   session that would end at its first close is refused at the door instead, where the person can be told
   why. This follows `UnlockGate.enroll()`'s own contract — "the caller must decide up front, not at the
   locked screen".
4. **Only `establishSession` may call `SessionManager.establish` or `UnlockGate.enroll`** in production
   code. `Scripts/check-biometric-contract.sh` enforces it, plus the presence and order of the steps; two
   baits in `rehearse-gates.sh`.
5. **A rejected refresh ends the session in the shell, without the wipe.** `SessionManager.sessionEnded`
   turns true and the shell returns to Login; the token entry is all that is erased (ADR-0036 point 1),
   and the rest goes at the next `establishSession`.

## Consequences

- The login slice (S1.1) has one call to make, and cannot forget the key or the slate.
- A failed-attempt count left by a dead session does not survive into the next one. A new sign-in is a full
  re-authentication with the IdP (password + TOTP), which is what the ceiling exists to force.
- What is NOT decided here: how a shared phone gets back into a session (task `0016`), and whether a
  temporary platform error should end one (task `0019`). Both are the author's.
- Pinned by `SessionEntryTest` (six cases, including the order of the first three calls) and by the
  `sessionEnded` case there.
