# ADR-0044 — The access flow is the design handoff's, provisional, and begins no session

- **Status**: Accepted (2026-10-11) — the author's answers of this date, in session.
  **Amended by ADR-0056 (2026-10-13):** the doctor-linking screens and the welcome screen's doctor link are removed; a
  doctor signs in with RUT and password like anybody, and the server says which app opens.
- **Amended** (2026-10-14) by ADR-0058: the access flow stops being the handoff's provisional one; a patient signs in with RUT, password and TOTP, enrolled by a clinic's code.
- **Supersedes**: ADR-0043 (the sign-in flow copied from LumeMed).
- **Related**: ADR-0001, 0002, 0003, 0006, 0013, 0033, 0037, 0045; task `0029`; the handoff in
  `tareas/PENDING/Handoff/` (`03-Specs/1-Auth.md`); `ADR-0031 del backend` (T5), its tasks `0117`–`0120`.

## Context

The author delivered a design handoff for the whole app. Its access flow is not LumeMed's: a patient
**creates an account alone** with RUT, password and an SMS-verified mobile, recovers the password by SMS, and
a doctor has no account here — they **link** the LumeMed one with an eight-character code. None of that is in
the backend's contract (verified against `packages/contracts/openapi.json`, 2026-10-11):

- the patient tier enrols only through a code the practice issues (T5, `ADR-0031 del backend`); there is no
  self-signup, no SMS, no password recovery — a lost factor is re-enrolled in person — and TOTP is mandatory
  on every request;
- there is no doctor-linking operation, and the non-clinical doctor audience is unbuilt (its task `0117`).

The author chose to build the design's screens now, against local mocks with the design's shape, and to decide
the patient access model later (2026-10-11).

## Decision

1. **`features/auth/` draws the handoff's access screens**, patient and doctor, on LumeUIComposer (ADR-0033),
   with the copy and client-side validations of `03-Specs/1-Auth.md`, every user-facing string localized
   (ADR-0045).
2. **The seams have the design's shape and say so**: `core/auth/AccessGateway.kt` —
   `PatientAccessGateway` and `DoctorLinkGateway` — marked PROVISIONAL. Every rule the design names
   (attempts, lockout, code lifetime, resend interval, neutral recovery answer) is the server's; the app
   shows what it answers.
3. **What is wired is a scripted gateway** with synthetic fixtures that reach every edge state, chosen in
   `App()`. Its fixtures are listed in task `0029`.
4. **No session begins.** No call returns tokens, nothing is written to the secure store,
   `establishSession` is not called. The flow ends in a placeholder for the main app. ADR-0003 and the
   successor ADR-0006 point 3 demands stay untouched — that ADR is still owed before any patient session code.
5. **LumeMed's TOTP and challenge screens leave the flow** (the design has none); git keeps them. The lock
   screen (`unlock/`) stays: it belongs to the session, not to the access flow.

## Deviations from the handoff, each with its reason

1. **No «Ahora no» on 1c and no «Seguir con contraseña» on 1c-e1**: no Lume app runs without biometric
   re-entry (ADR-0037 amended; the author confirmed it on 2026-10-11). 1c-e1 offers the phone's settings or
   «Volver al inicio».
2. **No «¿Ya no tienes ese teléfono? Contáctanos» on 3a**: there is no support channel; a link to nowhere is
   worse than none (ADR-0043's fourth deviation, kept).
3. **The QR scanner on 4a and its camera-permission state 4a-e2 wait**: a camera needs a dependency ADR, and
   the obvious decoder (ML Kit) is GMS, on the denylist. 4a takes the typed code only, and 4a-e1's «Escanear de
   nuevo» reads «Escribir otro código».
4. **The welcome carousel on 1a is the kit's** `LumeCarousel`, requested and authorized on 2026-10-11 and
   delivered the same day: this app writes no UI of its own.
5. **~~The step progress is `LumeStepIndicator`~~ — closed 2026-10-12**: the kit draws the design's segmented bars, the step on the left and «Sigue: …» on the right (the author asked for it as drawn).
6. **~~The wordmark is pending its SVG~~ — closed 2026-10-12**: the kit's brand lockup draws the app icon and the name in its three inks with the link symbol, from the handoff's assets. The kit's face sets the name about 8 % wider than the design's.
7. **The accent is the kit's derivation of `#3E63DD`** (`LumeBrand(seed = …)`); its strong and light tones are
   computed, not the design's literal hexes.
8. **Phone and tablet**: the handoff says both; ADR-0002 keeps v1 phone-first, portrait.
9. **The design's example RUTs fail módulo 11** («18.456.789-2», «12.987.654-3»): the script uses valid synthetic
   ones, and a field shows the design's error for the design's own example.
10. **The 6-digit code input is the kit's** (2c, 3b): a label, a clear button and smaller boxes, where the design has
    bigger bare boxes that turn red on a wrong code. The author left it as the kit draws it (2026-10-12).
11. **One screen the design does not draw: «Tu cuenta aún no está enlazada a tu consulta»**, in 4b-e1's shape. The
    backend answers `not_provisioned` to a doctor's account no practice has linked (its ADR-0090), and the author asked
    for the state in the mock (2026-10-12). The script reaches it with the code `S1NENL4C`.
12. **2b's links open nothing yet**: «términos de uso» and «Ley 21.719» are drawn as links, as the design does, but there
    is no terms document to open; they wait for it (legal). They stay links in 2b-e1 too, by the kit's author's call.
13. **2a asks for the names in four fields** — first and second name, first and second surname, in two rows — where the
    design has «Nombres» and «Apellidos». The second of each is optional. The app still shows the first name and the
    first surname («Camila Soto», «CS»). The author's call, 2026-10-12.
14. **2b has a third consent the design does not draw**: since ADR-0052 (2026-10-13), Crashlytics and Analytics, sent to
    Google without the person's name or appointments (it was ADR-0051's crash reports with a crash-only key). It is
    optional and never marked missing; Configuración has the switch to withdraw it.

## Consequences

- When the access model is decided, either the seams become the backend's (and this ADR is superseded), or
  the backend reopens T5 (and the backend's ADR-0031 is). Building on the design's shape is a bet the author
  made knowingly.
- The password rules shown live are the design's. Wired to an IdP, they must equal its policy or go.
- The scripted gateway is not a mock *of the backend* — it is a mock of a contract that does not exist. It must
  never be mistaken for one: its name, its KDoc and this ADR say so.
