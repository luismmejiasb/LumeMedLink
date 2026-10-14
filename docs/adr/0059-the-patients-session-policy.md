# ADR-0059 — The patient's session policy

- **Status**: **Proposed** (2026-10-14). It is the successor of ADR-0003 that ADR-0006 point 3 requires before any
  patient session code. Points 1–5 compose decisions already accepted elsewhere; point 6 is new and waits for the
  author. Until the author accepts it, no patient session code is written (task `0028` stays frozen).
- **Supersedes**: ADR-0003's «patients: deliberately undecided».
- **Related**: ADR-0005, ADR-0011, ADR-0014, ADR-0032, ADR-0034, ADR-0036, ADR-0037, ADR-0041, ADR-0058; backend
  `ADR-0035` (patient tier, TOTP), `ADR-0037` (one phone), `ADR-0070` (token lifetime).

## Context

ADR-0003 left the patient tier open on purpose, until the backend created it. The backend has (its `ADR-0035`), and
ADR-0058 fixed how a patient signs in. What remains is the session around it: how long it lives, what ends it, and how
a person comes back. Most of the answer already exists, written for the doctor, and the threat that matters most here
is the shared phone (§8.17).

## Decision

1. **Signing in** is RUT, password and the TOTP code, every time a session begins (ADR-0058; backend `ADR-0035`). The
   session begins through `establishSession` only (ADR-0037): an empty slate, the tier-2 key, then the tokens. No
   biometrics, no session.
2. **The token is the IdP's**: about one hour (backend `ADR-0070`), refreshed by the IdP's client. The refresh
   credential is a tier-1 secret and lives in the secure store (ADR-0005). An IdP client that keeps it anywhere else
   is not adopted.
3. **One phone**: the session enrols the installation (`enrolMyPatientPhone`) as it begins, and every request carries
   `X-Lume-Installation` (ADR-0041; backend `ADR-0037`).
4. **Coming back** is the tier-2 unlock, anchored to key material (ADR-0011), after the inactivity window closes
   (ADR-0032, the same window as the doctor's). The unlock-attempt ceiling ends the session (ADR-0034).
5. **What ends it, and what each erases:**

   | Event | Erases | Then |
   | --- | --- | --- |
   | «Cerrar sesión» | everything (ADR-0014) | the welcome screen |
   | refresh rejected | the session's entry only (ADR-0036, ADR-0037 point 5) | sign-in, the rest goes at the next `establishSession` |
   | `session_superseded` | everything | a screen that says another phone signed in (ADR-0041) |
   | unlock ceiling reached | everything | sign-in |
   | absolute lifetime reached (point 6) | the session's entry | sign-in with TOTP |

6. **Proposed, for the author: an absolute lifetime of 30 days.** Past it, the person signs in again with password and
   TOTP, whatever the refreshes did. A shared phone (§8.17) should not keep a patient signed in forever behind only a
   biometric that the family may share. The number is the author's call.

## Consequences

- When the author accepts it, task `0028` can be unfrozen as soon as the backend builds requests `0010` and `0011`.
- Point 2 constrains the choice of the IdP client, which ADR-0058 defers to its own ADR.
- Re-entry without biometric hardware stays task `0026` (frozen); this ADR does not change it.
