# ADR-0030 — A Kotlin-only change must reach the iOS binary

- **Status**: Accepted
- **Date**: 2026-09-21
- **Amends**: ADR-0028's account of the stale framework. Does not derogate it: what it says is true
  and was only half the chain.

## Context

F7 found that the iOS host had been linking old Kotlin since 2026-08-25, because the build phase
never set `KOTLIN_FRAMEWORK_BUILD_TYPE`: the Kotlin Gradle plugin printed a *warning*, did not
refresh `build/xcode-frameworks`, and the link used whatever was left there while the build stayed
green. That was fixed and gated.

**It was the wrong link of the chain — or rather, only the first one.** Measured on pristine code,
on 2026-09-21:

```
change ONE Kotlin string literal, nothing else
  → Gradle rebuilt the framework, and the new literal IS in it
  → the app binary: same sha256, same mtime, literal ABSENT
  → xcodebuild: BUILD SUCCEEDED
```

`KOTLIN_FRAMEWORK_BUILD_TYPE` made Gradle's **output** fresh. Nothing made the **link** consume it.

The cause is structural, not a typo. `composeApp/build.gradle.kts` declares `isStatic = true`, and
the framework reaches the linker through `OTHER_LDFLAGS = -framework LumeMedLink` plus
`FRAMEWORK_SEARCH_PATHS`. The target's `Frameworks` build phase is **empty**. So there is no input
Xcode's build system tracks that changes when Kotlin changes, and the link task is considered
up to date. A Swift edit forces the link and the current Kotlin lands with it, which is why this
was invisible: it only bites when the change is Kotlin ONLY — which is most changes in this repo,
and every one that an iOS device measurement would be about.

This is the same defect as F7's, one step downstream, and it is the reason the rule below matters
more than the fix: **when you repair a staleness, ask which link of the chain you repaired and go
check the next one.**

## Decision

**1. The build phase forces a relink when the framework moved.** It stamps the framework's mtime
and size in `$DERIVED_FILE_DIR`, and when the stamp differs it deletes the linked product
(`$TARGET_BUILD_DIR/$EXECUTABLE_PATH` and the debug dylib beside it). Deleting an output is the one
thing Xcode's build system does understand, and the script phase runs before Sources and
Frameworks, so the deletion happens before the link that will recreate it.

A no-op build costs nothing: measured at 2s with no forced relink and the binary untouched, because
Gradle leaves the framework alone when it is up to date.

**2. The declarative route was tried FIRST and measured not to work.** Adding the framework to the
`Frameworks` build phase as a `PBXFileReference` with `sourceTree = BUILT_PRODUCTS_DIR` — the
obvious, cleaner fix — does **not** cause a relink: the entry there is a **symlink** that Gradle
creates, and its own mtime never moves when the binary inside is rewritten. The first build after
that change did relink and looked like success; it relinked because the *project file* had changed.
A second Kotlin-only change with the project untouched showed the binary unmoved.

That near-miss is recorded because it is the shape this repo keeps falling for: **the first
measurement after a fix is confounded by the fix itself.** The test that counts is the second one.

**3. A gate for the mechanism, a verifier for the behaviour.** `check-ios-host.sh` asserts the
stamp and the deletion are written (ADR-0029: the call, not the word), with two baits in
`rehearse-gates.sh`. No grep can answer "did the linker re-run", so
`Scripts/verify-ios-link-freshness.sh` runs the experiment: four Xcode builds, and a **live control
that removes the guard and requires the hole to come back**. If the control does not reproduce the
hole, the script reports INCONCLUSIVE rather than success — a control that cannot fail proves
nothing, and this repo has been fooled by exactly that on this exact file.

## Consequences

- **Every iOS observation made after a Kotlin-only edit, on any machine, before today, measured the
  previous build.** That is a wider window than F7's two weeks, because F7's window closed on
  2026-09-07 and this one never closed. Specifically it re-opens the question for anything measured
  between 2026-09-07 and today; the measurements of the audit itself are unaffected, since they were
  taken with markers verified byte-for-byte in the linked binary.
- CI is not affected: a clean checkout has no previous binary to keep, so its first link is correct.
  The defect bites the author's machine, incrementally — which is precisely where device
  verification happens.
- The verifier is by hand and costs four builds. It runs **before trusting any iOS measurement** and
  after touching the build phase.
- **Declared, not fixed:** the same question exists for the Android side of the KMP build and has
  **not** been measured. Gradle tracks its own inputs there, so there is reason to believe it is
  fine — but reason to believe is what this ADR is about.
