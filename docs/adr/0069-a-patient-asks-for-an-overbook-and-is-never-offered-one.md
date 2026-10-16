# ADR-0069 — A patient asks for an overbook; she is never offered one

- **Status**: Accepted (2026-10-16). The author's decision by form, on three questions put to them with the platform's
  contract 0.135.0 in hand.
- **Related**: ADR-0001 and constitution §1.0 (the data boundary), ADR-0007 (no clinical documents), ADR-0046 (the
  handoff's booking rules), ADR-0060 (`FeatureGate`), ADR-0061 (the generated client), ADR-0067 (only a screen's own
  operation fails it), the platform's `ADR-0105` and its task `0157`, backend request `0017`.

## Context

On 2026-10-15, in the platform's session, the author decided that a patient may **ask** a professional's practice for
an overbook from LumeMedLink, and the platform built it (contract 0.135.0): `requestMyOverbook`,
`listMyOverbookRequests` and `withdrawMyOverbookRequest`. The practice grants it with an hour, which reaches the
patient as any booking does, or declines it, which she is told by e-mail. Only a professional whose rule
`patientRequests` allows it can be asked, and that rule is **off by default**.

Three things in this repo stood against wiring it as built:

1. The handoff says «El **sobrecupo nunca se ofrece** al paciente» (`Handoff/00-Producto.md`), and ADR-0046 lists
   «no overbooking» among the booking rules.
2. A request carries a reason from a closed list, and two of its four values name more than a date does: «Revisar
   resultados de exámenes» and «Renovar una receta». §1.0 says of an appointment: never the clinical reason.
3. The directory does not say which professional takes requests. Any other answers `404`, which does not say why.
   With the rule off by default, a button shown to everyone would fail for nearly everyone.

## Decision

1. **Asking is not offering.** The app never shows an overbook as a bookable hour, and availability never lists one.
   It lets the patient ask, only where the professional opened it. This amends ADR-0046's «no overbooking» to that
   reading.
2. **The four reasons, bounded.** The sheet where she asks offers the platform's four, which name no symptom and no
   result. The reason is shown **only on that sheet**, where she picks it. It never goes into a notice, a push, a
   pre-auth surface, a log or an analytics event, and the list of her requests shows the professional, the clinic,
   the status and the date, never the reason. «Renovar una receta» asks for an hour; it moves no prescription
   (ADR-0007 is untouched).
3. **The entry stays hidden until the directory says who takes requests** (backend request `0017`). Meanwhile the
   rest is wired behind its own feature, `OVERBOOK_REQUESTS`: asking, listing and withdrawing, with one `clientId`
   per attempt, reused on the retry and renewed when what is sent changes.
4. **An urgency is not an emergency.** The sheet says that an emergency goes to an emergency service, not to a
   request a practice reads in office hours. The number it names is Chile's, behind the country seam (ADR-0034 of
   LumeMed).

## Consequences

- The vendored contract moves from 0.131.0 to 0.135.0.
- `check-data-boundary.sh` admits `RENEW_PRESCRIPTION` as a whole identifier and nothing else: the value must be sent,
  so it exists in the app. A name that contains it, or a clinical name on the same line, still fails, and two baits
  in `rehearse-gates.sh` prove it.
- A request recorded by the practice over the phone (`origin = PHONE`) shows in the same list as hers.
- Until `0017` is built nobody can ask from the app; the list and the withdrawal work for what the practice
  recorded.
- If the directory never carries the rule, the fallback is to offer the button to everyone and read the `404` as
  «no recibe solicitudes», which the author turned down for now.

## Placement (2026-10-16, the author's decision by form)

- **The list is a section of «Citas»**, «Solicitudes de sobrecupo», above the upcoming appointments and only when
  there is one. Each row: the professional, the clinic, the status and the day it was asked; a waiting one can be
  taken back.
- **The entry is in the results**, as a secondary action under «Agendar» on a professional who takes requests,
  stressed when they have no hour within the horizon. It appears once request `0017` is built.
- The platform took request `0017` as its task `0161` (its commit `9cf81e0d`): one boolean on the directory's
  professional, read from the same view that decides `requestMyOverbook`, and `false` at a clinic where the patient is
  not enrolled.

