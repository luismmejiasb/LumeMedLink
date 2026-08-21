# ADR-0021 — The release build states its own posture

- **Status:** Accepted · 2026-08-21 (fortification slice F21, partial)
- **Related:** §8.11; threat model T6; ADR-0018 (the supply chain that ends at one laptop).

## Context

`androidApp/build.gradle.kts` had **no `buildTypes` block at all**. AGP's defaults happen to be
correct for release, but a default is not a decision and nothing asserted it — a one-line edit
could have shipped a debuggable release with every gate green.

It matters because of what debuggable means: `run-as` and `adb pull` reach the app's entire private
data directory, including the encrypted session store. And the debug build is the one the author
sideloads onto a device for verification.

## Decision

Both build types declare themselves. `release` is `isDebuggable = false`; `debug` says
`isDebuggable = true` **explicitly**, so nobody reads the absence as a claim — a debug build IS
reachable by `run-as` and must never carry a real doctor's token.
`Scripts/check-release-hardening.sh` asserts it, with comments stripped before the check because
this file explains what a debuggable release would mean and three earlier gates in this repo were
fooled by exactly that.

**`isMinifyEnabled` stays off, as a decision.** R8 on a KMP + Compose app needs keep rules this
project has never exercised; shipping an untested shrinker is a bigger risk than the
reverse-engineering it would slow down. Registered as owed work rather than switched on blind.

## What F21 does NOT contain, and why

The rest of F21 — Play Integrity, App Attest, root/jailbreak detection — is **blocked**, not
skipped:

- **Play Integrity and App Attest are server-verified by design** (§8.11 mirrors ADR-0010 of
  LumeMed: defense in depth, never a guarantee). Their verdicts mean nothing without a backend to
  check them, and there is no deployed backend.
- **App Attest additionally needs the iOS host** that does not exist.
- Local root/jailbreak detection without server attestation is a check the attacked device performs
  on itself. It is worth having as depth, and worth nothing alone — so it lands with its server
  half, not before.

The gate asserts the source declaration, not the built artifact. Asserting on the APK requires a
build and belongs with the device checks.
