# ADR-0053 — One catalog and one resolver for links and notification taps

- **Status**: Accepted (2026-10-13). It is the author's request for a deep-link base that serves push notifications,
  and these are the author's calls of the same day, by form:
  - the notice centre is catalogued, without a screen;
  - paths are Spanish, with values in the query;
  - Analytics hears which destination a link opened.
- **Related**: constitution §8.5 and §8.12; ADR-0012 (`PushSignal`, pre-auth surfaces), ADR-0038 (push conditions),
  ADR-0045 (navigation), ADR-0052 (Analytics' closed catalog); tasks `0011` (domain verification, FREEZE) and `0012`
  (the push client, FREEZE).

## Context

Two things will open screens from outside: links (universal links, App Links) and notification taps. Neither exists
yet. Links need a production domain (task `0011`), and the push client waits for the backend (task `0012`). The
screens they would open do exist now: the patient's app and the doctor's.

A common way to route links is a chain of routers, each asking "can I handle this path?", where the first to say yes
wins. It fails in two known ways:

- two routers that claim the same path are resolved by registration order, which nobody reads;
- a router whose test is too broad (any path ending in a given word) swallows links meant for others.

Keeping a central list of paths fixes naming, not ownership.

## Decision

1. **One catalog: `DeepLinkEntry`.** Each place a link may open has one entry.
   - The path is the identity, matched exactly: not by prefix, not by suffix, with no traversal and no encoded slash.
   - Paths are Spanish (`citas/detalle`, `configuracion/recordatorios`), because the people who read them are.
   - Each entry's metadata is declared by exhaustive `when`s without `else`: its audience, the one value it requires,
     and whether a push may carry it. A new entry does not compile until those are decided.
   - `DeepLinkCatalogTest` pins every published path by hand, because a path renamed after a message went out breaks
     that message.
2. **Values travel in the query, and only as `OpaqueRef`s.** For example `citas/detalle?cita=<id>`.
   - A required value has to be present exactly once and be well-formed. Prose cannot become an id (ADR-0012).
   - Any other query parameter, such as a campaign's `utm_*`, is ignored rather than refused.
   - Links are accepted over https only, on the app's exact host. That host list is empty until the domain exists, so
     today no outside link opens anything.
3. **One slot behind the lock: `LinkCenter`.** No link opens anything before sign-in or while the session is locked,
   not even on top of the lock (§8.12).
   - The link waits in a single slot, in memory only, for five minutes on the elapsed clock.
   - The newest arrival replaces an older one.
   - Sign-out and logout forget it, so one person's link never opens in the next person's session.
4. **One resolver, no chain.** `resolve(link, session)` returns one of three answers: open it, hold it, or drop it.
   - A link for the other role is dropped, not re-aimed "close by".
   - Each host maps entries to its screens with an exhaustive `when`: `openPatientLink`, `openDoctorLink`.
   - Whatever was open (an appointment's flow, a rating sheet) closes first, so a link never lands underneath it.
   - **A link grants navigation, never access.** The appointment detail opens only if the id is one of the patient's
     own appointments, as the gateway lists them. Another id lands on Mis citas and goes no further.
   - A link never performs an action. It opens a screen, and the person acts from there.
5. **Notification taps take the same road.** A data-only payload holds `kind` and, optionally, `link`, a relative
   catalogue path (`pushNoticeOf`).
   - The kind keeps choosing the app's own copy (ADR-0012). New kinds: `APPOINTMENT_REMINDER`, `APPOINTMENT_UPDATED`,
     `RATING_INVITED`, `OFFICIAL_NOTICE` and `ACCOUNT_CHANGED`.
   - **The destination is held to the kind.** A tap on «recordatorio de cita» cannot open the privacy switch. A link
     that is malformed, outside the catalog, absolute, or outside the kind falls back to the kind's own place. So a
     server cannot widen anything by writing a different link.
   - `SESSION_REVOKED` opens nothing.
6. **Entries without a design land on the closest screen that exists**, declared in each host:
   - the notice centre (`avisos`, `avisos/detalle`) lands on Inicio, where its bell is, and on the agenda for a
     doctor;
   - Configuración's rows (`configuracion/recordatorios` and the rest) land on Configuración.

   No screen is invented. When the design draws one, its host line changes, and the entry and its published path stay.
7. **Analytics hears the destination, never the id.** The event `deep_link_opened` carries two details:
   `destination` (the entry) and `origin` (`link` or `push`).
   - ADR-0052's catalog gains closed details for this: the key is an `AnalyticsParam`, and the value is the name of an
     enum constant of this code, never a string from data.
   - `check-firebase-posture.sh` asserts that both platforms build the details only from `analyticsValues()`.
   - The event is logged when the link opens, not when it waits.

## What is not built here

- **The platform intake.** Neither the iOS `onContinueUserActivity` nor the Android intent data is wired yet; they
  enter with task `0011`, with the domain and its association files. Then the host list gets its first entry.
  - On Android, `MainActivity` is exported, so any app can send it an explicit `VIEW` intent with a URL, bypassing App
    Links verification.
  - The defence is this ADR's design, not the verification: the host check, the exact catalog, the lock, and "a link
    grants navigation, never access".
- **The notification itself.** The `FirebaseMessagingService`, the APNs registration and the displayed copy enter with
  task `0012`. The copy keys (`push.*`) exist; their strings come with the notifications that show them.
- **The notice centre's screen,** which waits for the design.

## Rejected

- **A chain of routers by registration order.** That is the ambiguity this replaces.
- **Ids in the path** (`citas/<id>`). The match stops being exact, and every entry needs its own segment parser.
- **Opening a link on top of the lock.** §8.12 says no link relocates while locked, and a screen under the lock is
  still a screen.
- **Persisting a waiting link.** A link that survives the process survives a hand-over of a shared phone.

## Consequences

- Adding a destination means:
  1. a catalog entry;
  2. its metadata, which does not compile without it;
  3. its pinned path in the test;
  4. its line in each host's `when`.
- The backend's push payload contract is this one: `kind` from `PushSignalKind`, and `link` from the catalog. It goes
  to the backend with task `0012`, when requests reopen.
- Task `0011` keeps the domain half: the association files, the intent filters with `autoVerify`, the associated
  domains entitlement, and wiring both platforms' intake to `LinkCenter`.
