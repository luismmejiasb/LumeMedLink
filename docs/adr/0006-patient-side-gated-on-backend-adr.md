# ADR-0006 — The patient side is gated on a backend ADR that does not exist yet

- **Status:** Accepted · 2026-08-17
- **Related:** `ADR-0031 del backend` §4 (the exact demand); trampa T5 del feature-gap; ADR-0003.

## Context

`ADR-0031 del backend` says it in full: *"A self-service patient portal is explicitly out of scope
and must not be smuggled in under 'compliance'. It is a second authentication tier, a role outside
the `Membership` model, a different consent and identity-proofing regime, and its own threat model…
It needs its own ADR and its own slice."* And trampa T5 warns that even an expiring retrieval link
is one design review away from being exactly the prohibited thing.

LumeMedLink's patient half **is** that surface, arriving on purpose instead of smuggled. This ADR
makes the gate explicit so no slice builds patient features against a platform that cannot
authenticate a patient.

## Decision

1. **No patient-facing feature ships — or is even wired against a mock — until the backend accepts
   the ADR that creates the patient tier**: identity + proofing, consent regime, role/authz model
   outside `Membership`, and its threat model. That ADR is the backend's to write; this repo's job
   is to request it (`docs/backend-requests/0001`, first slice of the patient phase) with this app's
   concrete needs: profile read/write, own-appointments read, tele-consult signalling.
2. **Until then this app is doctor-only**, and says so in its login surface — no "patient? coming
   soon" account creation stub, because a stub collects credentials against nothing.
3. When the gate opens, the patient tier's session policy (MFA, recovery) lands in a successor to
   ADR-0003 — not silently inside a feature.

## Consequences

- The WORKPLAN's phases are honest: Fase 1 (médico) has no dependency on this gate; Fase 2
  (paciente) starts with the backend request, not with UI.
- Anyone proposing a patient feature cites this ADR's status first. The gate is a prerequisite, not
  an obstacle: it is the backend's own condition for doing this safely.
