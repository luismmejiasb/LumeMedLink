# ADR-0019 — The data boundary, as something a build can fail on

- **Status:** Accepted · 2026-08-21 (fortification slice F14)
- **Related:** ADR-0001 (the boundary itself); §13 (boundary violation as the class above Critical);
  the backend's warning nº3 in `ECOSYSTEM-STATUS.md` §3.1.1.

## Context

ADR-0001 is not a coding standard — it is the product. §13 says clinical content on any surface of
this app "no es un bug, es otro producto". Until now the rule was marked **[manual]**, honestly,
because no lint understands semantics.

The backend then named the exact shape that makes a manual rule insufficient: its `Patient` carries
`bloodType` (encrypted) and `careDirective` **in the same row** as email and phone, so — their
words — "una lista de operaciones permitidas admite el cuerpo entero de la respuesta". A DTO
written or generated from that entity brings clinical fields across unless something refuses them.
**Scope is not projection.**

## Decision

**A name gate, and it says it is one.** `Scripts/check-data-boundary.sh` fails the build on a
clinical term appearing as an identifier (`val bloodType`) or as a decoded contract field
(`@SerialName("careDirective")`), in Spanish and English.

It cannot catch a clinical value in a field called `notes`, and the script says so in its own
header rather than implying coverage it lacks. What it catches is the overwhelmingly common case:
the field is called what it is. That moves §13's first bullet from **[manual]** to **[lint,
partial]** — the honest label.

The vocabulary is deliberately specific. `note` alone would fire on every code comment, and a gate
with false positives is a gate someone disables — the lesson F13 already paid for.

**And the other half is a contract request, not client-side pruning.** `docs/backend-requests/0002`
asks for a non-clinical **projection as its own resource** — not a flag, not an optional parameter:
a parameter gets forgotten, a distinct type cannot be confused. Pruning on the client would still
mean the clinical bytes crossed the network, entered a response body, and passed through this app's
memory; the boundary is only real if they never arrive.

## Consequences

- A clinical field cannot enter this codebase silently, in either language.
- The gate reports its own limit; nobody should read a green as "no clinical data anywhere".
- Until the projection exists, the app decodes only the fields it is allowed to hold. The gate's
  second rule enforces exactly that at the `@SerialName` level.
- **Open, and deliberately not decided here:** whether `address` belongs in the boundary. The
  backend's accepted patient tier includes it; §1.0's closed list does not. Registered in
  `PROGRESS.md`; request 0002 leaves it out and asks the backend to say so out loud rather than
  inherit it.
