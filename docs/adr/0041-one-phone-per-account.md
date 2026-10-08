# ADR-0041 — One phone per account; a sign-in elsewhere ends the other session and warns the owner

- **Status**: Accepted — the author's decision of 2026-10-07 (task `0016`).
- **Related**: ADR-0003 (session policy), ADR-0011 (the tier-2 gate), ADR-0014 (logout = hard wipe), ADR-0037
  (session entry), `docs/backend-requests/0007`, task `0027`.

## Context

Task `0016` asked how a person gets back into a session on a SHARED phone, because biometrics do not tell people
apart: any finger or face enrolled on the phone opens the session. The author answered the premise instead: **the
phone is not shared.**

## Decision

1. **LumeMedLink works on one phone per account at a time**, as banking apps do. Both roles.
2. **Signing in on a different phone ends the session on the other one immediately**, and the owner is warned
   **preventively, by email and SMS**, that a session was opened on a new phone.
3. **The server decides and enforces it**; the app cannot. The new session's sign-in revokes the old phone's
   tokens, and the old phone, on its next request, receives a refusal it can tell apart from an ordinary
   expiry — and answers with the **full wipe** of the logout contract, not the token-only erase of a rejected
   refresh (ADR-0036 point 1), plus a message that says what happened.
4. **"A different phone" means a different installation**: a per-install key made at sign-in and registered with
   the server. A reinstall on the same phone counts as a new phone and triggers the same warning — the
   conservative reading, and the honest one, since the app cannot prove it is the same hardware.
5. **The two codes, the revocation and the warnings are the backend's.** Asked for in `backend-requests/0007`.
   The client half waits on it and is frozen (task `0027`).

## What it does not close, declared

A finger or face of someone else **enrolled on the owner's own phone** still opens the session: one-phone-per-
account does not change what the phone's biometrics accept. What this ADR adds is that the phone is the
owner's by rule, so the sign-in flow (S1.1) tells the person so in plain words — "this phone must be only yours:
any fingerprint or face registered on it can open your session" — and the threat model keeps the residual.

## Rejected

- An app PIN: homemade authentication (§8.2).
- The device passcode as fallback: the family knows it, and it voids the tier's invalidation.
- Asking the owner to approve the new phone before the old session ends: slower than the threat, and the
  author chose to close first and warn.

## Consequences

- "Immediately" is server-side. The old phone learns at its next request; the app is online-only and re-asks
  the server when it comes back to the foreground, so in practice that is the next time anyone looks at it.
  A data-only push could make it truly immediate once push exists (ADR-0038).
