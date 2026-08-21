# ADR-0020 — One logging path, and its default is silence

- **Status:** Accepted · 2026-08-21 (fortification slice F22)
- **Related:** §8.1; ADR-0016 (the network log line); ADR-0018 (slf4j shipping).

## Context

§8.1 says the redacting facade is the single point of logging. Nothing enforced it: `org.slf4j`
ships in the release APK (dragged in by `kotlinx-coroutines-slf4j`), `android.util.Log` was
importable anywhere, and a `println` compiled green.

Why logging is a security surface here and not hygiene: anything written to logcat at **any**
priority leaves the device inside `adb bugreport` — a zip the doctor can send to anyone, on a
**release** build, surviving a reboot. One stray `Log.d(TAG, patient.toString())` in a future slice
is a patient record in a support attachment.

## Decision

**`core/logging` is the only place this app writes a log line**, and its facade takes a **closed
set of events**, never a string. A facade taking `String` is a facade someone eventually hands a
patient's name; here there is nowhere to put one. `LogDetail` accepts a number, a duration, or an
enum **constant** name — validated — so prose is refused at the call site instead of by memory.
Same shape as `PushSignal` (ADR-0012), for the same reason.

**The default implementation writes nothing, and that is a decision.** Given where logcat goes,
silence is the correct posture until this app has a reason to log that survives that fact. The seam
exists so that day costs one file.

**Two gates, because an import ban is not enough.** detekt's `ForbiddenImport` covers
`org.slf4j.*`, `kotlinx.coroutines.slf4j.*` and `android.util.Log` outside `core/`;
`Scripts/check-logging.sh` covers what imports cannot — fully-qualified calls, `println`,
`System.out`, `NSLog`/`os_log`, and `printStackTrace`, which is its own leak because an exception
message can carry a URL, as this repo has already had happen.

## Consequences

- The network stack keeps its own `NetworkLogSink`: a different, equally constrained shape. Two
  seams, neither taking a caller-supplied string.
- A crash's stack trace still reaches the OS crash log — that is the platform's, not ours. What
  this ADR removes is our own contribution to it.
- **Not covered:** a value laundered through an enum constant name, and anything a third-party
  library logs on its own. The second is why ADR-0018's denylist matters more than this ADR.
