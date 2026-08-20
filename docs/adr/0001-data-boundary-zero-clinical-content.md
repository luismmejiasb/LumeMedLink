# ADR-0001 — The data boundary: zero clinical content, a closed list of what is handled

- **Status:** Accepted · 2026-08-17
- **Related:** LumeMed's whole §8 (what this boundary keeps out of this app); `ADR-0031 del backend`
  (why the patient side waits); ADR-0006, ADR-0007 of this repo.

## Context

The Lume family already has an app that carries PHI: LumeMed, whose constitution treats security as
the nervous system precisely because a lost iPad can expose a clinical record. LumeMedLink exists to
**separate environments**: everything around the consultation that is not the record — profile,
appointments, contacts, later the patient end of the tele-consultation — lives here, on phones, for
two audiences (doctors' non-clinical side, and patients once the backend can authenticate them).

The temptation this ADR exists to kill arrives early and looks reasonable every time: "since the
patient is already in the app, show them their prescription / their lab result / a note". Each of
those single features would silently convert this app into a second PHI surface with LumeMed's whole
threat model and none of its controls.

## Decision

1. **This app never handles clinical content.** Not the record, not diagnoses (including bare CIE-10
   codes), not notes, not lab results, not vitals, not medications, not allergies, not clinical
   documents. Neither rendered, nor cached, nor relayed as an opaque blob.
2. **What it does handle is a CLOSED list** — profile (name, photo, phone E.164, email, previsión),
   appointments (existence, date, place/modality, with whom — never the clinical reason), the
   doctor's patient list as a contact book, and in the future the tele-consultation's signalling.
   Extending the list is an ADR, not a feature.
3. **The boundary lowers risk and scope, never the standard.** An appointment with a specialist
   reveals health information; a RUT + name is full personal data under Ley 21.719. §8 of the
   constitution applies entirely — what the boundary buys is that a lost phone exposes an agenda,
   not a record.

## Consequences

- A feature that needs clinical content is either LumeMed's (doctor) or does not exist yet
  (patient). The answer is never "just this one field".
- Backend requests from this repo must not ask for clinical fields in its DTOs; the review checklist
  for every contract proposal includes the boundary.
- The threat model (docs/security/threat-model.md) is scoped by this ADR: it protects personal data
  and health-revealing metadata, and it says so instead of borrowing LumeMed's PHI language.
