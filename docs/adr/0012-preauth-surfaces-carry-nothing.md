# ADR-0012 — Pre-auth surfaces carry nothing, and a push payload cannot express content

- **Status:** Accepted · 2026-08-21 (fortification slice F2)
- **Related:** §8.5 of this constitution; threat model T2 (shared device, ranked first);
  ADR-0010 (the on-screen half); ADR-0001 (the data boundary).

## Context

A **pre-auth surface** is anything a person sees without unlocking anything: the lock screen, the
notification shade, the launcher's shortcut list, a home-screen widget. This app has none of them
today, which is exactly why the decision belongs here — the first slice that adds push will
otherwise make it by accident, in one line.

The leak is not hypothetical and it is not small. "Recordatorio: tu control de diabetes" on a lock
screen is a **health disclosure** under Ley 21.719, made to whoever is holding the phone — and this
app's first-ranked threat (T2) is precisely that a patient's phone is used by their family. The
boundary of ADR-0001 does not help here: the app never stores a diagnosis, but a notification
composed from an appointment reason would print one anyway.

## Decision

**1. No pre-auth surface exists in v1.** No widgets, no dynamic shortcuts, no lock-screen bypass
(`showWhenLocked` / `turnScreenOn`), no notification permission requested. A surface the app does
not have cannot leak, and each one returns only through its own reviewed slice.

**2. When push arrives, the payload is structurally incapable of carrying content.** The only
shape allowed is `shared/PushSignal`: a closed set of *reasons to wake the app* plus an optional
`OpaqueRef`. There is **no text field**. The user-visible copy is chosen by the app from the kind
(`displayCopyKey`), never supplied by the server — so no backend, and no compromised backend, can
decide what appears on a locked screen. `OpaqueRef` validates on construction and rejects anything
shaped like prose, so content cannot be smuggled through the id either.

This is the difference between a rule and a foundation: "do not put clinical text in a
notification" is a sentence someone can forget; a payload type with nowhere to put text is a
sentence the compiler enforces.

**3. Content is fetched authenticated, inside the app, after unlocking** (§8.5). The signal wakes;
it never informs.

**4. `Scripts/check-preauth-surfaces.sh` gates all of it** — notification builders and text,
public visibility, shortcuts, widgets, lock-screen flags, and the manifest's permissions and
receivers. Rehearsed against eight baits.

## Consequences

- **Android push collides with this repo's own denylist, and the collision is now on the record.**
  Verified, not assumed: adding `com.google.firebase:firebase-messaging` (the only push transport
  Android offers) is refused by `check-dependency-allowlist.sh` on two entries — Firebase and Play
  Services — and `com.google.firebase.*` is additionally banned by detekt's `ForbiddenImport`.
  §8.1 wrote that denylist against Analytics/Crashlytics; FCM is a different product that arrives
  through the same door. **Android push is therefore impossible today without an explicit decision
  by the author**, and this ADR deliberately does not make it: narrowing a security denylist is
  not a side effect of a notification slice. Registered as an open decision in `PROGRESS.md`.
- **The platforms are asymmetric again, and it is declared:** iOS push is APNs, native, with no
  third-party SDK and no collision. So a future push slice may well ship on iOS while Android waits
  on the decision above — which is a product consequence, not a technical surprise.
- The generic copy is deliberately vague ("algo te espera"). Vagueness is the feature; a user who
  wants detail opens the app and authenticates.
- A notification, when it exists, must also set non-public visibility — the gate already refuses
  `VISIBILITY_PUBLIC` so that the future slice cannot skip it.
