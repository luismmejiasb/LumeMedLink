# ADR-0023 — The security-event channel carries kinds, and says when it is not carrying them

- **Status:** Accepted · 2026-08-21 (fortification slice F23, partial)
- **Related:** §8.16; the ecosystem board §3 (LumeMed's no-op channel).

## Context

§8.16 has this app reporting security events to `POST /v1/security-events` with opaque kinds and
never content. The endpoint exists. The client does not, because the auth flow does not.

There is a specific failure to avoid, and it is not hypothetical: **LumeMed wired this channel to a
no-op that discarded everything**, and the platform never received a single event while its table
sat verified-and-writable. The gap was invisible because the seam looked implemented.

## Decision

**A closed set of opaque kinds, as a type.** `SecurityEventKind` has no field for a message, a user,
a device or a record. A reporting channel that accepts free text is the one that eventually carries
"unlock failed for Dr. Pérez, patient 11111111-1" into a server log. The names describe a **class**
of event, never an instance, and a test asserts each is an opaque constant short enough not to hold
a sentence.

**It fails silently, by doctrine.** The family's fail-direction table says this channel does not
throw: every caller is in the middle of applying a protection, and a backend being down is not their
problem.

**The stand-in is named for what it is.** `NoOpSecurityEventReporter` — not `DefaultReporter`, not
an empty implementation of the interface. LumeMed's lesson is that a no-op wearing a neutral name
reads as working; this one cannot.

## Consequences

- The HTTP implementation lands when the auth flow does. Until then this app reports **nothing**,
  and both the type name and this ADR say so.
- The kinds chosen map to events this app can actually detect today: unlock refused, tier-2 key
  invalidated, session ended unrecoverably, origin refused, secure store unreadable, interstitial
  detected. Adding one is a deliberate edit to a closed enum.
- **Open question for the backend**, already in request 0001: whether these kinds are registered on
  their side or whether the app must reuse LumeMed's set.
