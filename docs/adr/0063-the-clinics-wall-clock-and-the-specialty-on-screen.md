# ADR-0063 — The clinic's wall clock, and the specialty on screen

- **Status**: Accepted (2026-10-14), the author's decisions by form.
- **Related**: ADR-0018 (dependencies under control), §0 (multi-country: the country is the tenant's), §8.5 and §8.17
  (nothing personal on pre-auth surfaces or in notifications), ADR-0052 §3 (the analytics catalog), backend request
  `0013`, task `0036`.

## Context

The contract's patient forms give every hour as an instant in UTC (`startsAt`, `endsAt`). The design draws them on
the clinic's wall clock: «miércoles 21, 10:30». Chile changes its offset twice a year, and the family serves more than
one country (§0), so a fixed offset is wrong for part of the year and wrong outright for a clinic elsewhere.

Two things are missing:
- the clinic's zone, which the contract publishes on the practice's forms (`AgendaDto.timeZone`,
  `PracticeLocationDto.timeZone`) and not on the patient's;
- in the app, a time-zone database. Kotlin's standard library has none.

LumeMed also flagged the specialty: `specialtyCode` comes with every appointment, and a visit to an oncologist
reveals health. The design shows it.

## Decision

1. **`kotlinx-datetime` 0.8.0 enters `commonMain`** (JetBrains, the Kotlin team's own multiplatform date library). It
   reads each platform's own zone database (`java.time` on Android, `NSTimeZone` on iOS), so it ships no data of its
   own to keep current. Its group is already in `config/dependency-allowlist.txt` (`org.jetbrains.kotlinx`), and this
   ADR is the reason it entered.
2. **An instant is shown on its clinic's wall clock, never the phone's** (LumeMed's rule). The zone comes from the
   contract (request `0013`). Until the backend publishes it, the app uses `America/Santiago`, the platform's own seed
   zone, in one named constant. It is the one place to remove when the field arrives, and no other code assumes a
   country.
3. **The specialty is shown inside the app only** (the author, by form). It appears on the screens after sign-in:
   appointments, their detail, «Mis médicos» and the directory. It never appears in a notification, on the lock
   screen, in the app's recents snapshot (already covered, ADR-0010), or in analytics. Its name comes from the
   platform's catalog (`listSpecialties`), never from a list in the app.

## Consequences

- `CalendarDate` and `ClockTime`, the app's own date types, stay what the screens use. `kotlinx-datetime` stays inside
  `core/`, where instants become wall-clock dates. Screens do not import it.
- When request `0013` lands, the constant of point 2 goes, and an appointment without a zone becomes a contract error
  instead of a guess.
- `listSpecialties` joins the operation allowlist (ADR-0061).
