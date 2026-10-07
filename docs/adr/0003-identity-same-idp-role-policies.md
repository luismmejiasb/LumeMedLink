# ADR-0003 — Identity: the family's IdP, per-role MFA policy, patient tier deliberately open

- **Status:** Accepted · 2026-08-17 (patient tier: **deliberately undecided**, see ADR-0006)
- **Related:** ADR-0007 de LumeMed (Identity Platform, MFA TOTP, session policy); `ADR-0031 del
  backend` §4.

## Context

The platform authenticates clinicians through Google Identity Platform with mandatory TOTP
(LumeMed's ADR-0007). Doctors using LumeMedLink are the same humans with the same accounts. Patients
do not exist as identities anywhere in the platform today — the backend's ADR-0031 states a patient
account is "a second authentication tier, a role outside the Membership model, its own threat
model", requiring its own ADR.

## Decision

- **One IdP for the family**: Google Identity Platform. Never home-grown auth, hashing or sessions —
  if a task asks for it, stop and ask (the family rule).
- **Doctors**: same account as LumeMed, MFA TOTP mandatory, access token ≤15 min, refresh rotation.
  Signing into LumeMedLink is signing into the same identity with a narrower scope: the token this
  app requests must never grant clinical reads — scope separation is part of the backend request.
- **Patients**: the policy (MFA or not, identity proofing, recovery) is NOT decided here. It belongs
  to the backend ADR that creates the patient tier (ADR-0006 gate). This repo only fixes the floor:
  IdP-backed, short access tokens, rotating refresh, and the §8 storage rules.

## Consequences

- The app ships doctor-only until the patient tier exists; the login screen is built against the
  existing clinician flow.
- A "narrow scope" clinician token is a contract request (backend-requests/) before the first
  authenticated read: reusing LumeMed's full-scope token here would put clinical read capability
  inside the lower-boundary app — exactly what the environment separation exists to prevent.

## Amendment, 2026-10-07 — the access token lives one hour, and that is the platform's decision

The decision above lists "access token ≤15 min" for doctors. That number was never reachable: Identity Platform inherits
its ID tokens from Firebase Authentication, which issues them for one hour with no setting to shorten them. The platform
recorded it on 2026-10-03 in `ADR-0070 of the backend`, accepted by the author: the access token lives **one hour**, the
verifier rejects any token whose `exp − iat` exceeds 3600 s, and what compensates the hour is that authorization is
resolved from the database on every request. This app does not own that number and does not restate it as its own.

What it does own is unchanged, and is what protects the shared device (§8.17) in the meantime: the inactivity window and
the biometric re-entry anchored to key material (ADR-0011, ADR-0032, ADR-0034). Refresh with rotation stands.

