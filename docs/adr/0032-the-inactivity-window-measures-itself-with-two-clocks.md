# ADR-0032 — The inactivity window measures itself with two clocks, and closes on its own

- **Status**: Accepted
- **Date**: 2026-09-21
- **Related**: F4 (ADR-0011, the tier-2 gate this window opens), §8.3, §8.17 and threat model T2 —
  the shared device, which this repo ranks FIRST.

## Context

Two defects in the same seven lines, both found by audit, both invisible to every gate and test that
existed. They are worth stating separately because they fail in different ways.

**1. The window was measured with the wall clock alone.**

```kotlin
return clock.nowEpochMillis() - last >= windowMillis
```

The wall clock is a **setting**. Move the phone's time backwards and the subtraction goes negative,
so the window never elapses and the session never locks. On the threat this app puts first — the
doctor's phone in the hands of someone else in the house — that is a lock the holder can switch off
in Settings, without a password, in fifteen seconds.

The obvious repair, "use a monotonic clock", is half a fix and fails the other way: on both
platforms there are elapsed clocks that **stop while the device sleeps**, and a phone in a pocket
for three hours is the ordinary case, not the exotic one. A window measured only that way would come
back unlocked after a night on the nightstand.

**2. Nothing ever asked whether the window had closed.**

The shell kept `locked` as a copy of the lock's state and re-read it in exactly one place: the
pointer handler. So five minutes could elapse with the agenda on screen and the app would notice
**when somebody touched it** — which is the one moment the person is already looking at the screen.
The threat is a phone left on a table, and a phone left on a table has no touches in it.

The lock logic was correct. It was simply never asked.

## Decision

**1. Two clocks, and the window closes when EITHER says it elapsed.**

```kotlin
val elapsed = maxOf(byWallClock, byElapsedClock)
```

Each covers the other's failure, and the choice does not depend on being right about which platform
pauses which clock:

| failure | wall clock | elapsed clock | `maxOf` |
| --- | --- | --- | --- |
| user winds the time backwards | never locks | unaffected | **locks** |
| device asleep, elapsed clock paused | unaffected | never locks | **locks** |
| user winds the time forwards | locks early | unaffected | **locks early** — the harmless direction |

A new `ElapsedClock` seam in `core/` asks each platform for the same thing, spelled out rather than
inherited: **time since boot, including the time the device spent asleep.** Android:
`SystemClock.elapsedRealtime()` — not `uptimeMillis`, not `nanoTime`, which stop in deep sleep.
iOS: `clock_gettime(CLOCK_MONOTONIC)`, which on Darwin is documented as continuing to increment
while the system sleeps, unlike `CLOCK_UPTIME_RAW` and `ProcessInfo.systemUptime`.

**Honest about the evidence:** the iOS sleep behaviour is read from Apple's documentation and
**not measured** — a simulator does not sleep and this session had no device to sleep. If the
documentation were wrong, the failure direction is the safe one: the wall-clock half still closes
the window, which is exactly where this class was before.

**2. The window closes on its own.** `InactivityLock.millisUntilLock()` exposes the remaining time,
clamped at zero so a caller can never hand a negative number to `delay()` and spin. The shell sleeps
on it and re-asks after waking: if activity slid the window meanwhile, it simply gets a new positive
number and sleeps again. No polling interval to tune and no restart to orchestrate.

**3. Both halves are gated.** `check-biometric-contract.sh` asserts that the lock reads BOTH clocks
and takes the larger, and that the shell asks for the remaining time and sleeps on it — as calls,
with comments and string literals stripped (ADR-0029). Two baits in `rehearse-gates.sh`.

## Consequences

- Five new tests in `InactivityLockTest`, each seen red with its own bait: measuring by wall clock
  alone, measuring by elapsed clock alone, and dropping the clamp. The pre-existing tests were
  rewired to inject **both** clocks — they had been injecting one and silently inheriting the real
  system clock for the other, which is the kind of test this repo does not want.
- **The risk that existed was real but not reachable today**, and saying so plainly matters: the
  app has no login, so it never navigates to `Home` and the window never runs in production. What
  was fixed is a defect that would have shipped with the first login slice, in the control that
  slice exists to protect.
- **The clock seam is now two seams and they are not interchangeable.** `Clock` names an instant and
  is right for token expiry, which is compared against a server-issued absolute time.
  `ElapsedClock` measures a duration. A future reader reaching for `nowEpochMillis()` to time
  something is reaching for the defect this ADR closed.
- Not addressed here, and named so it is not mistaken for covered: the failed-attempt ceiling lives
  in memory, so killing the process resets it. The OS biometric subsystem has its own lockout, which
  bounds the damage, but the ceiling this app believes it enforces is not the one enforced. It needs
  its own slice.
