# ADR-0048 — The map apps open with the clinic's address and nothing else

- **Status**: Accepted (2026-10-11) — the author's decision of this date, for f1 «Cómo llegar».
- **Related**: ADR-0007 (amended here, narrowly, as ADR-0047 did), ADR-0001, ADR-0027 (the patient's address is not this),
  ADR-0029; `Scripts/check-forbidden-patterns.sh` P2; task `0030`.

## Context

f1 lists the map apps installed on the phone (Mapas, Google Maps, Waze) and opens the chosen one at the appointment's
address. On Android, handing an address to another app is `Intent.ACTION_VIEW`, one of the intents P2 refuses because a
view intent can hand a file to anything (ADR-0007). Finding which apps can open an address is a query with the same
intent. On iOS it is `openURL`, with `canOpenURL` to learn whether Google Maps or Waze is there, which needs their
schemes declared in `LSApplicationQueriesSchemes`.

## Decision

1. **One door, one shape.** `core/device` builds one intent: `ACTION_VIEW` on a `geo:0,0?q=` URI carrying the address,
   encoded. It is used to list the apps that answer it and, with the chosen app's package, to open it. Nothing else is
   put on it. On iOS the links are Apple Maps' `https://maps.apple.com/?q=`, Google Maps' `comgooglemaps://?q=` and
   Waze's `https://waze.com/ul?q=`, and `LSApplicationQueriesSchemes` names only `comgooglemaps` and `waze`.
2. **Only the clinic's address.** The value is the appointment's place — where a doctor attends, which the practice
   publishes. Never the patient's own address (ADR-0027), never coordinates the app does not have, never a route from
   the patient's location (constitution §1.0: no «cerca de ti»).
3. **The gate keeps the rest of ADR-0007 total.** P2 lets that one line pass, and only in
   `core/device/DeviceDirections.android.kt`. A second view intent there, anything put on it (an extra, a type, a clip
   or a stream), or a view intent anywhere else, is red. `Scripts/rehearse-gates.sh` carries a bait for each.
4. **The icons are the platform's.** Android hands over each installed app's own icon. iOS does not let an app read
   another's, so there the kit's glyph stands in, with the app's name.
5. **One app, no sheet.** When only one app answers, it opens directly (spec f1).

## Consequences

- The map app gets an address. What the patient does there is theirs and never comes back to this app.
- The handoff wants the pin on the door, through coordinates. The backend does not geocode (its task `0118`), so the
  address is sent as text, and the map app finds it as text.
