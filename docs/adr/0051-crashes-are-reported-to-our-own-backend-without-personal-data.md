# ADR-0051 — Crashes are reported to our own backend, without personal data

- **Status**: **Derogated by ADR-0052 (2026-10-13)**: the author lifted the Firebase prohibition and Crashlytics
  replaces this report. Two of its decisions survive there: a crash travels without its message (point 1), and the
  consent is optional and can be withdrawn (point 4). The capture, the queue and the transport are removed by task
  `0032`, and request `0009` is withdrawn. What follows is the record as accepted.
- **Was**: Accepted (2026-10-12) — the author's decisions of this date, taken by form after weighing Crashlytics
  against a report of our own. The client half is **built** (task `0031`, 2026-10-12) and sends nothing yet
  (`NoEndpointCrashReportSender`); the backend half waits for `docs/backend-requests/0009`, written and **not sent**,
  while the author keeps requests to the backend paused.
- **Related**: constitution §8.1 (the crash/analytics denylist), §8.5, §8.13, §8.16; ADR-0012 (pre-auth surfaces),
  ADR-0016 (exception messages that carried the URL), ADR-0023 (the security-events channel), ADR-0038 (the one
  Firebase exception), ADR-0044 (2b's consents); task `0031`.

## Context

The app has no way to learn that it crashed. §8.1 denies every third-party crash or analytics SDK by name, and that
holds: a crash reporter ships what the process held when it failed, and in this repo an exception message already
carried a URL with a RUT in its query (ADR-0016). With the identity provider on Google Identity Platform, Crashlytics
would be one line away. The author weighed a narrow Crashlytics exception, built the way ADR-0038 built FCM, against
a report of our own to our own backend, and chose the second.

## Decision

1. **A report has a closed shape, and there is no field for free text.** It carries:
   - the exception's type;
   - up to eight stack frames **from this app's own packages**, each a class and a function, plus a line number;
   - the app's version and build;
   - the platform and the OS version;
   - the day it happened.

   It never carries the exception's message, a cause's message, a thread's name, a frame from a library, or anything
   typed or shown. Messages are where personal data hides (ADR-0016). A frame name is code, not data.
2. **No account and no phone identifier.** The report never travels with `Authorization` or with
   `X-Lume-Installation`, the key that binds a phone to an account (request `0007`).
3. **A crash-only key, and only with consent.** A random key, distinct from every other identifier the app holds,
   lets the backend tell one person's repeated crash from many people's. It is pseudonymous personal data, so:
   - it is created only after the person consents;
   - it is deleted when consent is withdrawn and on logout (§8.13), so a shared phone does not join two people's
     crashes;
   - it is attached **when a report is sent, never when it is captured**: a queued report holds no key, so a report
     written before a withdrawal or a logout cannot leave with the key of whoever is there now;
   - until consent, a report carries no key at all.
4. **The consent is optional and can be withdrawn.** It is asked beside 2b's two required consents, as a third row
   that is **not** required, and it can be withdrawn in Configuración. A consent the person cannot refuse is not
   consent under Ley 21.719. 2b gains a row the design does not draw (ADR-0044, deviation 14).
5. **Sent at the next launch, before any sign-in.** A crash ends the process, so the report waits for the next launch.
   It goes to an unauthenticated endpoint through the hardened stack (§7), like the minimum-version check. A crash
   before sign-in is exactly the one worth seeing, and there is no session to protect.
   - At most three pending reports are kept.
   - Each is a small file in the app's own directory, excluded from backup (§8.5), deleted after the server accepts
     it or after seven days. It holds no key and no personal data (point 1), which is why it is not encrypted.
     Android keeps it under `noBackupFilesDir`; iOS marks the directory `NSURLIsExcludedFromBackupKey` (seen on the
     simulator). `check-backup-posture.sh` asserts both, with two baits.
   - There is one attempt per launch and no retry loop. The endpoint has no idempotency key (trap T13), so a retry
     would turn one crash into several.
6. **What is caught, declared.**
   - On Android: uncaught JVM exceptions, through the default handler, which then hands over to the system's own.
   - On iOS: uncaught Kotlin exceptions, through Kotlin/Native's unhandled-exception hook. Its frames carry no line
     numbers; an internal function's symbol is read the other way round (`pkg.Class.function#internal`).
   - Not caught: native crashes outside Kotlin (Swift, Objective-C, signals) and Android ANRs. They are said here
     rather than implied.
7. **It never stops a crash.** Capturing writes the file and hands control back to the platform; a failure while
   writing is swallowed. A crash handler that throws hides the first crash behind its own.

## Rejected

- **Crashlytics with a narrow exception.** It would still upload the message and the stack it captures, start before
  sign-in unless taken apart as FCM was, and open a second Firebase exception beside ADR-0038.
- **Reusing `POST /v1/security-events`.** Its vocabulary is closed and opaque on purpose (ADR-0023). A crash is not a
  security event, and widening that channel is how a channel starts carrying free text.
- **Only the type, the version and the platform.** That tells us something broke, but not where.
- **The phone-binding key as the report's identifier.** It joins a crash to an account; the author chose a key of its
  own.

## Consequences

- Task `0031` builds the client half: capture, the closed report, the bounded queue, the third consent, Configuración's
  switch, and a transport that sends nothing until the backend exists, under its own honest name (the lesson of
  `NoOpSecurityEventReporter`).
- Request `0009` asks the backend for the endpoint. It waits until the author reopens requests.
- The privacy notice gains a line on crash reports, with or without the key.
