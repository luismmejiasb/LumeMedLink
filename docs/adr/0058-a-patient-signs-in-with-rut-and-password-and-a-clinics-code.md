# ADR-0058 — A patient signs in with RUT and password, enrolled by a clinic's code

- **Status**: Accepted (2026-10-14), the author's decisions by form, after the backend's inventory of the same day.
- **Amends**: ADR-0044 (the access flow stops being the handoff's provisional one) and ADR-0056 (the server, not a
  sign-in attempt, says which side opens).
- **Related**: ADR-0003, ADR-0006, ADR-0041 (one phone per account), ADR-0059 (the patient's session policy),
  ADR-0060 (`FeatureGate`); `ADR-0035`, `ADR-0036`, `ADR-0037` and `ADR-0090` of the backend; trap T5 (§1.2);
  backend requests `0010` and `0011`.

## Context

On 2026-10-14 the author reopened the wiring of the app against the platform. The backend's inventory (session
`lumemed-cloud-platform`, contract 0.126.0) showed four ways in which the access flow built from the handoff
(ADR-0044) differs from what exists:

1. The platform authenticates with **Google Identity Platform**, one IdP tenant per app tier, and the tier comes
   from the token's audience. There is no operation before authentication that says whether an account is a
   patient's or a doctor's, so ADR-0056's single door does not work as written.
2. **TOTP is mandatory for every human account, patients included** (backend `ADR-0035`).
3. A patient account is **enrolled by a code the clinic hands out** (`redeemPatientEnrolment`, 15 minutes, single
   use). Self sign-up is the self-service portal that T5 forbids.
4. The backend **never exposes the RUT as an identifier**, and Identity Platform signs in by e-mail or phone.

The author weighed the risks of a RUT as a username (they were set out before the form): a different answer for an
unknown RUT reveals that a person is a patient somewhere; anyone who knows a RUT can lock its owner out; each clinic
records its own RUT, and they may differ. The author chose the RUT anyway, as Chilean banks do, with those risks
mitigated by the server.

## Decision

1. **A patient signs in with RUT and password**, then the TOTP code. The server maps the RUT to the IdP account
   before authenticating (request `0010`), under three conditions that are part of the request, not options:
   - the same answer and the same timing whether or not the RUT has an account;
   - attempts slowed down rather than locked hard, so that knowing a RUT is not enough to lock its owner out;
   - one RUT per account, fixed when the account is enrolled, whatever each clinic records later.
2. **The account is born from the clinic's code**, never from self sign-up. The handoff's sign-up screens (data, SMS
   code, password) become «I have a code from my clinic» → RUT and password → TOTP enrolment, reusing the handoff's
   visual language. The SMS code of the design's sign-up disappears with it.
3. **One IdP tenant for LumeMedLink, and the role in `/v1/me`** (request `0011`). The app signs in once, reads
   `/v1/me`, and the role decides which side opens. This keeps ADR-0056's single door, with the server as the only
   source of the role. Until the backend builds it, the doctor's side stays on its script.
4. **The role is a fact from the server, never a flag.** It feeds `FeatureGate` (ADR-0060). A Remote Config flag can
   turn a feature off, never on for a role that does not have it. The backend's tier checks stay the authority: the
   app's gate only decides what is drawn.
5. **Nothing of this ships before the backend has it.** Until requests `0010` and `0011` are built, the access gateway
   stays on its script in release builds, and debug builds use the local backend's dev token (ADR-0061).

## Consequences

- ADR-0044's flow is retired, and the access screens change: a redesign within the kit, task `0036`.
- The Identity Platform client is not chosen here. Firebase Auth is not among the products ADR-0052 allows, so that
  choice needs its own ADR, written when a GCP project exists to test it against.
- Password recovery depends on the IdP and on request `0010`. It is designed with them, not before.
- Tasks `0027` (one phone, client half) and `0028` (the auth gateway) stay frozen until the backend builds `0010` and
  `0011`.
