# ADR-0006 — The patient side is gated on a backend ADR that does not exist yet

- **Status:** **Superseded · 2026-09-15.** The gate this ADR declared is open: the backend ADR it
  demanded exists and its surface is built. The title is kept as written because it records the
  state of the world on 2026-08-17; read the Amendment below before citing this ADR for anything.
- **Superseded by:** `ADR-0035 del backend` (the patient identity tier) and the outgoing errand
  `docs/backend-responses/0003-patient-tier-ready.md` in `lumemed-cloud-platform`.
- **Still binding:** decision 3 (the patient tier's session policy lands in a successor to ADR-0003,
  never inside a feature). Decision 2 holds as a statement of fact for as long as no patient feature
  has shipped, but it is no longer a prohibition.
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

## Amendment · 2026-09-15 — the gate opened, in two steps

This ADR blocked the patient half on a backend ADR that did not exist when it was written. It does
now, and the block is lifted. The opening happened in two distinct steps, and the distinction is
what kept this repo honest in between:

1. **The decision arrived (2026-08-21).** The backend accepted `ADR-0035 del backend`, the patient
   identity tier. `PROGRESS.md` recorded it the same day with the caveat that mattered: the decision
   existed, the surface did not, so there was still nothing to build against and decision 1 of this
   ADR still forbade wiring against mocks.
2. **The surface arrived (2026-08-27).** The backend published the outgoing errand
   `docs/backend-responses/0003-patient-tier-ready.md`, which lands `ADR-0035 del backend` plus the
   HTTP half of `ADR-0036 del backend` and carries a section addressed to this repo's agent. The
   figures (contract version, paths, operation counts) live in the backend's `ECOSYSTEM-STATUS.md`
   and are deliberately not copied here.

**What this does not license.** The gate is open, not the work. Decision 3 stands: the patient
tier's session policy (MFA, recovery, the mandatory TOTP the backend decided on) belongs in a
successor to ADR-0003, written before any patient session code exists, not discovered inside a
feature slice. The data boundary of ADR-0001 and the document prohibition of ADR-0007 are untouched
by this amendment: a patient audience does not widen what this app may carry.

**Why this file was edited instead of a successor ADR being written.** This repo's convention is
that a correction lands in a new ADR which declares what it supersedes (see ADR-0024). That
convention is for corrections of *reasoning*. Nothing here was wrong: this ADR's reasoning held, its
precondition was simply met by another repo. What was wrong was the document's *status*, and leaving
a title that says "does not exist yet" on an `Accepted` ADR is precisely the defect this family
names first: the document that lies. Verified against `lumemed-cloud-platform` on 2026-09-15.
