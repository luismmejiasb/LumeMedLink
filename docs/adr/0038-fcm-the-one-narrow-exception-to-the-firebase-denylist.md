# ADR-0038 — FCM: the one narrow exception to the Firebase denylist

- **Status**: Accepted (2026-10-07). The decision is the author's, of 2026-09-26 (ADR-0036 point 3: FCM for
  Android, together with APNs); this ADR writes its conditions and controls, as task `0024` asks, **before**
  any Firebase artifact enters a lockfile.
- **Related**: §8.1 and §8.8 of the constitution, ADR-0012 (pre-auth surfaces), ADR-0016 (merged-manifest
  permissions), ADR-0018 (the dependency gate), `shared/PushSignal.kt`, task `0012` (the patient's
  appointments and their notices), the vault's legal review of 2026-10-04 (RL-13) and the backend's
  `FREEZE/0022`.

## Context

The constitution denies Firebase and Play Services by group (§8.1; `check-dependency-allowlist.sh`,
detekt's `ForbiddenImport`), because with this IdP Firebase Analytics is "one line away". Push on Android
exists only through `com.google.firebase:firebase-messaging`. The author chose FCM; the denylist stays the
rule, and what is needed is an exception that cannot widen by accident.

What `firebase-messaging` 26.0.0 actually brings, **measured on 2026-10-07** by walking its POMs (compile
and runtime scope) and reading the manifest of every AAR in the closure — not assumed:

- **Firebase**: `firebase-messaging`, `-common`, `-components`, `-annotations`, `-encoders` (+`-json`,
  `-proto`), `-datatransport`, `-iid-interop`, `-installations` (+`-interop`), and
  **`firebase-measurement-connector`** — the analytics *interop interface*, not analytics itself.
- **Play Services**: `play-services-base`, `-basement`, `-cloud-messaging`, `-stats`, `-tasks`.
- **`com.google.android.datatransport`** (`transport-api`, `-runtime`, `-backend-cct`): Google's own
  telemetry transport, which FCM uses for delivery metrics. It has `INTERNET` of its own.
- `androidx.datastore` (used by `firebase-common`), plus groups this app already ships.
- **Merged permissions**: `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `POST_NOTIFICATIONS` (declared by
  `firebase-messaging` itself) and `com.google.android.c2dm.permission.RECEIVE`.
- **Merged components**: `FirebaseInitProvider` (initializes Firebase at process start, before any login),
  the exported `FirebaseInstanceIdReceiver` (guarded by `c2dm.permission.SEND`), `FirebaseMessagingService`,
  and the datatransport scheduler service and receiver.

## Decision

1. **The exception is artifacts, never a group.** `check-dependency-allowlist.sh` keeps denying
   `com.google.firebase` and `com.google.android.gms`, minus an exact `group:artifact` list: the closure
   above. Analytics, Crashlytics, Performance, Ads, Measurement — anything else under those groups — still
   fails. A bump that adds a member to the closure stops at the gate for a decision. The groups themselves
   enter `config/dependency-allowlist.txt` only in the push slice, with the lockfile that brings them.
2. **The four merged permissions are decided here** and added to `check-network-posture.sh`'s allowlist
   now, so the push slice does not have to loosen a gate in the same change that needs it.
3. **Conditions the push slice must meet** (each is a requirement, not a wish):
   - **Data-only payloads.** The visible text is the app's, picked from `PushSignal.displayCopyKey`
     (ADR-0012); a payload never carries a title, a body, a name or a reason.
   - **No token before login, none after logout.** `firebase_messaging_auto_init_enabled=false` in the
     manifest; the token is requested after `establishSession` (ADR-0037) and deleted
     (`deleteToken()`) in the logout contract, which gains a step for it.
   - **Firebase initializes when the app says so.** `FirebaseInitProvider` is removed from the merged
     manifest (`tools:node="remove"`) and Firebase is initialized explicitly with options in code —
     which also keeps the `google-services` Gradle plugin out of the build (a plugin that runs with the
     build's privileges is the surface ADR-0018 locked).
   - **Analytics stays off twice**: `firebase_analytics_collection_deactivated=true` and
     `google_analytics_adid_collection_enabled=false`, though no analytics artifact may ship at all.
   - **The datatransport telemetry is MEASURED before release**: a network capture on the emulator of what
     the app sends to Google besides the FCM registration. If FCM's delivery metrics cannot be switched
     off, that is declared in the threat model and to the author — not assumed harmless.
   - **Google is declared as a processor** (subencargado) for the push token, as RL-13 requires.
4. **Imports**: detekt's `ForbiddenImport` keeps `com.google.firebase.*` refused outside `core/`, so the
   FCM service lives in `core/push/` and no feature can reach Firebase.
5. **Pre-auth surfaces** (`check-preauth-surfaces.sh`, ADR-0012) are amended **in the push slice**, with
   the seam they then exempt and its test — not here. Exempting a file that does not exist yet would be a
   gate loosened over nothing it can check.

## Rejected

- **Allowing the groups.** It is exactly the hole the denylist exists for: `firebase-analytics` would be
  one line away again, and green.
- **A third-party push relay** instead of FCM: another processor, another SDK, and Android still delivers
  through Play Services underneath.

## Consequences

- Nothing ships today: no lockfile carries a Firebase artifact. The bait `firebase-analytics shipped beside
  the FCM exception` (in `rehearse-gates.sh`) proves the exception is narrow — measured: messaging alone
  passes, analytics beside it fails.
- The push slice inherits a checklist, not a debate. Android push stays off until it lands (task `0012`).
