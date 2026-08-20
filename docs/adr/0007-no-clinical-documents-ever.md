# ADR-0007 — This app never displays or transports clinical documents

- **Status:** Accepted · 2026-08-17
- **Related:** ADR-0035 de LumeMed (nothing signs electronically); `ADR-0019 del backend` (amended);
  trampa T1; ADR-0001 (the boundary this sharpens).

## Context

The family's standing decision: a prescription, certificate or medical license is only legally a
document once printed, signed and stamped in ink. Delivering one **without printing** — attachment,
link, message — reopens Ley 19.799 entirely (electronic signature, SNRE, accredited providers) and
is out of scope by decision, not by omission.

A patient app is the most tempting delivery channel that will ever exist in this family. "The
patient is right there — just show them the PDF" will be proposed, repeatedly, and it would turn
this app into the exact surface T1/T5 forbid, while also breaching ADR-0001 (a prescription is
clinical content).

## Decision

- LumeMedLink **never renders, stores, relays, links to, or notifies about** a clinical document.
  Not as a PDF, not as an image, not as an expiring download link, not as a push payload.
- The vocabulary rule mirrors LumeMed's: no screen in this app may describe any document as
  «emitido / firmado / validado» — but here the stronger rule makes it moot: the document never
  appears at all.
- If the legal ground ever changes, the path is: backend re-opens its ADR-0019 first, LumeMed
  amends ADR-0035, and only then this repo revisits — in that order, because this app is the
  delivery surface, not the decision owner.

## Consequences

- Share sheets, file exporters and document pickers have **no legitimate clinical use** in this app;
  their appearance in a diff is a red flag (§13) and, once gates exist, a lint hit.
- The appointments feature shows the visit, never its artifacts.
