# ADR-0067 — Only a screen's own operation can fail it

- **Status**: Accepted (2026-10-15), the author's rule.
- **Related**: ADR-0055 (a screen waits as its own skeleton), ADR-0045 (a ViewModel per tab root), ADR-0029 (a gate
  asserts the mechanism where it runs), task `0037`.

## Context

Twice in one day a read that was not the screen's took the whole screen to its full-screen «Reintentar»: the bell's
count (`/v1/patient/notices`), then the appointments, both on Inicio. The author: an API call that is not the
operation the person is doing never shows an error, least of all full screen.

The cause was structural, not a slip. Every screen joined its reads with `allOf`, which makes ANY failure the
screen's. Fixing one call site at a time left the shape that produced the next one.

## Decision

1. **A screen has one operation of its own**, and only it can show the screen's failure and retry: Citas reads the
   appointments, Mis médicos the contacts, Ajustes the profile, Avisos the inbox, Buscar's results the search, a
   schedule the free hours.
2. **Everything else is a secondary read** (`core/care/SecondaryRead.kt`): `secondary(fallback) { … }` answers the
   VALUE, never a `CareResult`, so it cannot enter an `allOf` or become a failure. A failure leaves out what it
   would have drawn — the 24 h note, a visit count, the nearby communes, a clinic's name, the rating section — and
   never shows a false value in its place («0 visitas», «no tienes citas»).
3. **Inicio has no operation of its own**: every piece belongs to another screen. Its state is `HomeContent?` —
   waiting or ready — so a failure cannot be held at all. Without the profile it says «Hola»; without the
   appointments it draws neither the hero card nor «no tienes citas».
4. **A read in the background, behind a screen that is not its own** (the bell's count, the rating card on Inicio) is
   a `CappedSecondaryRead`: after three failures in a row it stops asking. Its own screen reads it with its own
   failure and retry.
5. **`Scripts/check-secondary-reads.sh`** keeps the two doors shut: Inicio's state type, and every `allOf` call site
   named with the one operation its reads make up together. Rehearsed with four baits.

## Consequences

- With the local platform down, every tab root still draws: only the tab whose own operation failed shows the retry.
- A fallback has to be a value that claims nothing: `null` and «left out», not zero or empty when empty is a
  statement. The tests assert the left-out shape (`aScreensSecondaryReadsNeverFailIt`, Inicio with every read failing,
  Mis médicos without the visits).
- The doctor's side is the script and keeps its one `allOf`, named in the gate.
