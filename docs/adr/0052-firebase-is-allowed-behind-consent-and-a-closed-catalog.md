# ADR-0052 — Firebase is allowed, behind consent and a closed catalog

- **Status**: Accepted (2026-10-13) — the author's decision, with its four conditions chosen by form the same day.
  **Derogates ADR-0051** (our own crash reports). **Supersedes point 1 of ADR-0038** (the artifact-by-artifact FCM
  exception); ADR-0038's conditions for push (its point 3) stay in force. Amends the constitution's §0, §8.1, §8.8
  and §13.
- **Related**: constitution §1.0, §8.5, §8.13, §8.15; ADR-0012 (pre-auth surfaces), ADR-0016 (an exception message that
  carried a RUT), ADR-0018 (the dependency gate and build plugins), ADR-0044 (2b's consents); task `0032`.

## Context

§8.1 denied every crash and analytics SDK by name, Firebase first, because with this identity provider Firebase was
"one line away". ADR-0038 opened one door, artifact by artifact, for FCM. ADR-0051, one day before this one, chose a
crash report of our own over Crashlytics.

The author now lifts the prohibition and adopts the suite: Crashlytics, Analytics, Remote Config, Cloud Messaging and
App Distribution. The author's reasons: the app holds personal and contact data, not clinical records; millions of
apps ship the suite; and no recent leak has been attributed to the SDKs themselves.

Two corrections are recorded, so that this decision does not rest on something the constitution contradicts:

1. **The app does handle data that reveals health.** §1.0 says so: an appointment with an oncologist is information
   about the patient's health, and under Ley 21.719 that is sensitive data. What the app hands to Google is the risk.
2. **The known incidents were not SDK flaws.** In the FTC's case against GoodRx (2023), with Google among the
   recipients, the SDKs did what they were built to do; the app put medication names into the events. The risk is
   the payload, and the payload is ours to close.

So the suite is allowed, and what it may carry is closed in code and asserted by gates.

## Decision

1. **Five products, each by its artifacts.**
   - Allowed: Crashlytics, Analytics without the advertising identifier, Remote Config, Cloud Messaging and App
     Distribution.
   - On Android, the allowed artifacts are the closure of those products under Firebase BoM 35.0.0, measured from
     the lockfile that brings them. On iOS they are the SwiftPM products `FirebaseCore`, `FirebaseCrashlytics`,
     `FirebaseAnalyticsCore` (Analytics without IDFA), `FirebaseRemoteConfig` and `FirebaseMessaging`, from
     `firebase-ios-sdk` 13.0.1.
   - Every other Firebase product stays denied until an ADR of its own: Firestore, Realtime Database, Auth, Storage,
     Performance, In-App Messaging, AI Logic, and Ads. So do the third-party crash and analytics SDKs §8.1 names
     (Sentry, Bugsnag, ACRA, Amplitude, Mixpanel…). §8.1 is narrowed, not erased.
2. **Crashlytics and Analytics collect only with consent.**
   - Both are off from the factory, by manifest and plist flags, so nothing is collected before the app decides.
   - They turn on when the person ticks 2b's optional row (the one ADR-0051 added) and turn off from Configuración.
   - Withdrawing deletes what the device still holds: Crashlytics' unsent reports and Analytics' local data.
   - Logout withdraws too, as part of the wipe (§8.13). A shared phone does not carry one person's choice to the
     next person.
3. **Analytics sends a closed catalog.**
   - The events are an enum in `core/telemetry/`. There are no free-form parameters.
   - No user ID, and no user properties.
   - No automatic screen reporting, on either platform.
   - An event never names a doctor, a specialty, a clinic, a date, a place or a reason. An event says that something
     happened, not to whom or with whom.
   - Only the platform adapter calls Firebase Analytics, and only with a catalog name. A gate asserts both.
4. **A crash travels without its message.**
   - A report carries the exception's type and its stack.
   - The message, and the messages of its causes, never travel. That is where personal data hides (ADR-0016), and
     the author already chose "the type and where in our code" (ADR-0051).
   - On Android, the app's handler hands Crashlytics a copy of the throwable that keeps the type's name and the
     frames and drops every message.
   - On iOS, an unhandled Kotlin exception is recorded with the type, the frames this app's parser reads, and an
     empty reason.
   - No custom keys, no breadcrumb logs, and no user identifier. A gate asserts it.
5. **Remote Config carries flags, never controls.**
   - Its keys are a closed enum, each with its default in code, so the app behaves the same before the first fetch,
     offline, or without configuration.
   - A remote value never carries personal data.
   - It never acts as the kill switch: that stays with the backend (§8.15).
   - It never switches off a security control: not `FLAG_SECURE`, the privacy cover, the lock, or a gate.
   - It is functional rather than analytical, so it runs without the analytics consent. Google is declared as its
     processor.
6. **Push keeps ADR-0038's conditions.**
   - Payloads are data-only, and the visible text is the app's own (ADR-0012).
   - There is no token before login, and the token is deleted at logout.
   - Delivery metrics are not exported.
   - The token reaches the backend only when its endpoint exists, and requests to the backend are paused.
   - **The client half waits with the backend** (the author's call, 2026-10-13): it is built in task `0012`, not
     before. The notification permission is asked right after the first booking (a3), for that appointment's
     reminders — never at launch.
7. **App Distribution is a release tool, not a library.**
   - Builds are uploaded with the Firebase CLI.
   - No tester SDK ships in the binary.
   - There is no Gradle plugin, for ADR-0018's reason: a plugin runs with the build's privileges.
8. **Firebase starts only when the app is configured.**
   - The project's configuration files (`google-services.json`, `GoogleService-Info.plist`) stay out of git, like
     anything that names infrastructure.
   - On Android, the options are built in code from that file at build time, so the `google-services` plugin stays
     out (ADR-0038).
   - `FirebaseInitProvider` stays removed, so nothing starts at process launch.
   - Without the files, Firebase is not initialised and every adapter is a no-op whose name says so.
9. **Google is a processor, and the transfer is declared.** The privacy notice names Google, what each product
   receives, and the international transfer that Ley 21.719 requires to be stated.

10. **Firebase talks to Google through its own transport — the one declared exception to §7.**
    - The SDKs open their own connections (`HttpURLConnection`, `URLSession`, Google's datatransport). They never
      carry this app's tokens, and they never reach this app's backend.
    - Everything that is the app's own still leaves through the hardened stack.
    - The exception is declared here rather than discovered in a network capture. What each product sends goes into
      the pre-release capture, per product (Consequences).

## What the build showed (2026-10-13)

- **The Analytics catalog** starts with six events:
  - `sign_up_completed`, `sign_in_completed`;
  - `appointment_booked`, `appointment_rescheduled`, `appointment_cancelled`, `appointment_rated`.

  None carries a parameter. A new event is a decision about what leaves for Google, reviewed like a dependency.
  **Amended by ADR-0053 (same day):** `deep_link_opened` is added, with closed details. The key is an
  `AnalyticsParam`, and the value is the name of an enum constant of this code. Free text still has no way in.
- **The Remote Config catalog is empty.** The plumbing is in place: defaults set, fetched at launch, and read through
  the enum. No flag exists until one is decided.
- **Crashlytics brings `firebase-sessions`.** It reports app sessions so Crashlytics can count crash-free users. It is
  a Crashlytics subscriber, so it follows Crashlytics' collection switch. That is to be confirmed in the pre-release
  capture, not assumed.
- **Analytics' Android closure asks for four permissions this app does not grant itself.** They are the advertising
  ID, two Privacy Sandbox attribution permissions, and the Play install referrer. All four are removed from the merge.
  `play-services-ads-identifier` and `androidx.privacysandbox.ads` still ship as code, with nothing granting them
  anything.
- **On iOS, SwiftPM resolves `firebase-ios-sdk`'s whole graph**, thirteen packages, even those only Firestore uses
  (abseil, gRPC, leveldb) and Google Ads' on-device conversion SDK. Resolving is not linking: the app links the five
  products. `check-ios-host.sh` pins the graph as measured, so a fourteenth package stops at the gate.
- **On iOS, one Kotlin crash shows as two Crashlytics entries.** The Kotlin hook records the exception with its
  Kotlin frames, and the abort that follows is then caught as a native crash without them. This is declared rather
  than hidden.
- **The Kotlin crash on Android reaches Crashlytics as `MessageFreeCrash`**, named after the original type, with the
  original frames. Grouping works on frames, so issues still group by where the code broke.

## Rejected

- **Keeping our own crash reports beside Crashlytics.** It means two captures to maintain for the same event, and a
  backend endpoint that waits on paused requests.
- **Collecting from the first launch with an opt-out.** It sends data before the person has chosen.
- **Free events per feature.** That is GoodRx's mechanism.
- **The GitLive Kotlin wrapper.** It is a third-party layer, with its own release pace, over the official SDKs.

## Consequences

- Task `0032` does the work: the dependencies, the adapters on both platforms, the consent wiring, and the gates with
  their baits.
- ADR-0051's capture, queue and transport are removed. Its consent row and Configuración's switch stay, and now
  govern Crashlytics and Analytics. Request `0009` is withdrawn, never sent.
- `check-dependency-allowlist.sh` stops denying Firebase as a group and denies the products above by name. A
  new gate checks iOS's `Package.resolved`, which no gate read before.
- ADR-0038's open measurement grows: before release, a capture of everything the app sends to Google, per product,
  goes into the threat model.
- The author creates the Firebase project and the APNs key. Until the configuration files exist, the app runs as it
  does today.
