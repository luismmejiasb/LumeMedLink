# ADR-0068 — A rating is stars and closed aspects, sent with its consent

- **Status**: Accepted (2026-10-15). The author delegated the call («hazlo tú») after it was put to them: drop the
  free text and write the consent.
- **Related**: ADR-0046 (the design as a provisional mock), ADR-0067 (only a screen's own operation fails it), the
  platform's `ADR-0095` (ratings), backend request `0008`, constitution §1.0 and §8.1.

## Context

The rating sheet (f5) came from the design with two free-text fields: «Otro» with a 40-character title, and
«Cuéntanos más». ADR-0046 kept them as a provisional mock. The contract the platform built (`rateMyAttention`, since
0.121.0) takes **stars and up to five closed aspects, never free text**, plus the person's consent and **the version
of the consent text the app showed**, which the platform keeps with the rating as its record. The app showed no
consent at all.

Free text next to a doctor's name is also where a patient writes their diagnosis: §1.0's boundary, by another door.

## Decision

1. **No free text.** «Otro» and «Cuéntanos más» are gone from f5, from `RatingDraft` and from `SentRating`. The
   aspects are the contract's closed list.
2. **The consent is a line on the sheet, above the send**, in the app's words (`rate_consent`): the rating counts in
   the doctor's public average, no practice reads it, and it can be withdrawn. Sending is consenting; the version
   (`RATING_CONSENT_VERSION`) goes with it. **Changing that text means a new version in the same change.**
3. **Wired over the contract** (`RemoteRatings`): the rateable attentions joined with the patient's own appointments
   (one that is not among them is left out, never shown nameless), the ratings given, and the rating sent with one
   key per attention until it lands, so a retry is answered with the rating written.

## Consequences

- The consent's wording was written by the app, not by legal review. It says only what the contract states; a lawyer
  may want it longer, and that is a new version.
- «Hacer un reclamo» (1–2 stars) still opens nothing: it is not a rating.
- Withdrawing a rating (`withdrawMyRating`) is in the contract and not yet on any screen.
