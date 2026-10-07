# ADR-0036 — The author's answers of 2026-09-26, landed

- **Status**: Accepted
- **Date**: 2026-10-07
- **Source**: the author's round of answers of 2026-09-26, recorded in the LumeBrain vault
  ("2026-09-26 — Ronda de decisiones del autor", section LumeMedLink), and confirmed by the author on
  2026-10-07 for landing here — §14 of the constitution asks that a vault note that orders something be
  confirmed before it is applied. Until today this repo still listed every one of them as open.

## Decisions

1. **A rejected refresh erases only the session's entry**, as in LumeMed — it is not a logout. This is
   what the code already does (`SessionManager`), and it closes the open question ADR-0014's 2026-09-21
   amendment left. Its consequence for a first cache is task `0020`.
2. **The bitácora index is deleted.** `docs/bitacora/README.md` had declared itself abandoned at entry
   0001; the directory listing is the index. The numbered-docs gate's exception for it goes with it.
3. **FCM for Android, together with APNs.** The constitution's Firebase denylist stays the rule; the
   narrow exception — `firebase-messaging` only, no analytics, data-only payloads, the token registered
   after login and deleted at logout — needs its own ADR and gate changes, written as task `0024`
   before any Firebase artifact enters a lockfile.
4. **Idempotency for `bookMyAppointment`**, with the same key as the clinic tier's `book` — the backend's
   work (its task 0008); this app sends the key when it books.
5. **Public store name: LumeMedLink.** Subject to the trademark check against Medilink the vault records
   (an INAPI search before any store listing); if that check fails, this decision is reopened.
6. **The patient side comes forward.** The constitution's "the doctor side first" (§0, §12) is replaced:
   the patient slices (WORKPLAN S2.x) may be built before the rest of FASE 1. Unchanged and still binding:
   the patient-tier session policy lands in a successor of ADR-0003 **before** any patient session code
   (ADR-0006 point 3), and the enrollment stays clinic-mediated.
