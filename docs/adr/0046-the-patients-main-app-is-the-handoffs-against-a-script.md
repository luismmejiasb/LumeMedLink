# ADR-0046 — The patient's main app is the design handoff's, against a script read from the contract

- **Status**: Accepted (2026-10-11) — follows the author's decisions of this date: build the handoff's screens
  against local mocks that follow the backend's contracts, and send what the contract lacks to the backend as a
  request.
- **Related**: ADR-0001, 0002, 0007, 0008, 0013, 0033, 0044, 0045; task `0030`; `docs/backend-requests/0008`;
  the handoff in `tareas/PENDING/Handoff/` (`2-App-principal`).

## Context

After the access flow (ADR-0044) the handoff draws the patient's main app: five tabs — Inicio, Buscar, Mis citas,
Mis médicos, Configuración — and the flows that hang from them (search to booking, an appointment's detail,
rating a visit, the profile photo). Unlike the access flow, most of it **has** a contract (0.96.0, read on
2026-10-11): `/v1/patient/appointments` (list, book, cancel, withdraw, reschedule, confirm),
`/v1/patient/professional-contacts`, `/v1/patient/profiles`, `/v1/directory/professionals/search` with its
availability, and the reference catalogs (specialties, communes). What it lacks is listed in request `0008`.

Two contract facts shape the screens and are not the design's:

- the patient profile is **per clinic** (`SelfProfileDto` carries a `tenantId`) and has no RUT;
- an appointment carries its time as **UTC instants**, one `locationLabel`, no street and no coordinates, and no
  professional id.

## Decision

1. **`features/patient/` draws the tabs**, one area with a screen per tab (`home`, `search`, `appointments`,
   `doctors`, `settings`), their shared parts in `parts/`, and the tab host in `tabs/`. One `ViewModel` per tab
   root (ADR-0045); one stack per tab over `LumeTabBar`, each stack only its root until a flow pushes onto it.
2. **The seam is `core/care/PatientCare.kt` — `PatientCareGateway` — marked PROVISIONAL.** Where the contract has
   the datum the type mirrors it; where it does not, the field is the design's and request `0008` asks for it.
3. **What is wired is `ScriptedPatientCare`**, the design's own sample (synthetic), anchored on the day the
   design draws — Wednesday 16 September 2026 — so «Hoy» lines up, with an empty world for the empty states and
   a failure switch for offline. Chosen in `App()`.
4. **Days and times reach the screens as wall-clock values** (`shared/CalendarDate`, `ClockTime`), not instants.
   Turning the contract's UTC instant into the clinic's day needs a time-zone database; that dependency and its
   ADR belong to the HTTP adapter, not to this provisional build.
5. **The patient's ViewModels live in a store of their own** (`app/SessionScope`), which a sign-out or a restart
   empties: what one patient's tabs read never survives in memory into the next attempt (§8.13).
6. **Still no session** (ADR-0044 point 4): a patient finishing the access flow lands here; nothing is stored.

## Deviations from the handoff, each with its reason

1. **No bell and no profile disc in Inicio's header, no calendar disc in Mis citas'**: `LumeNavBar` has no
   trailing slot, deliberately (phone-first), and this app writes no UI of its own. The author decides whether
   the kit grows one or the design moves them.
2. ~~The «Próxima cita» card is the kit's accent card~~ — closed 2026-10-11: the author authorised the kit's solid
   accent card and Inicio uses it.
3. **Rating stars are a list row** («¿Cómo fue tu atención?» with a star) until the kit has a rating control;
   the rating flow (f5) needs one. Showing a rating is the kit's `LumeRating` since 2026-10-11; choosing one is not.
4. **«Correo» in Mis médicos shows the address and does nothing**: handing it to the mail app is Android's send
   intent, one of the doors ADR-0007 closes without exceptions (`check-forbidden-patterns` P2). Opening that door
   for an address with no attachment is the author's call. «Llamar» opens the dialer with the number; it does not
   place the call.
5. **Mis médicos' filter is a field of the hardened primitive**, not the kit's search field: `LumeSearchField`
   takes no text case, and a typed name must not enter the keyboard's dictionary (ADR-0013).
6. **The avatar's camera badge in Configuración waits for the photo flow** (k1–k4), which has no contract: the
   profile has no photo field.

7. **Search to booking (p3–a4) leaves out four things the design draws.** «Más cerca» in p4 needs the patient's
   location, and a coordinate is no excuse for a «cerca de ti» (constitution §1.0, ADR-0027). «Previsión» in p4
   and a2: the contract has no coverage on a professional and books without one. «Motivo de la consulta» in a2:
   removed by the author, and a clinical reason has no place here (ADR-0001). «¿Ya sabes a quién buscas?» in p2:
   the design has no screen for a search by name, though the contract takes a `name`.
8. **a1 draws no taken hours.** Availability returns only free ones, so the design's struck-out hours are not
   drawn. Since 2026-10-11 the rest is the design's, from the kit: the month and year menus, the day strip with the
   days without hours refused, and an hour the patient already holds drawn as «Tu cita» with a line naming the
   clash.
9. **Rating, its count and the number of visits** in p3 come from the script (request `0008` §4); filtering and
   sorting run over what the search returned.

## Consequences

- The flows behind the tab roots (search to booking, detail, change, cancel, rating, photo, the doctor's three
  screens) are the next slices of task `0030`, each against the same seam.
- When the backend answers `0008`, the seam's fields are renamed to the contract's and the HTTP adapter replaces
  the script; the screens do not change.
- The booking rules of the handoff (working hours, minimum notice, maximum horizon, change window, no overbooking,
  the doctor confirms) are the server's: the app shows what availability and the appointment's `status` say.
