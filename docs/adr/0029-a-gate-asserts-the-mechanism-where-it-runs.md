# ADR-0029 — A gate asserts the mechanism, in the place where it runs

- **Status**: Accepted
- **Date**: 2026-09-21
- **Supersedes**: nothing. Amends the technique of the gates written in S0.2, F1, F3, F6, F12 and ADR-0025.

## Context

An external audit walked through two gates with bait their authors had not thought of. Both gates
were green with their control **deleted**:

1. `check-network-posture.sh` and `check-backup-posture.sh` filtered XML comments line by line
   (`grep -v '<!--'`). A comment spanning four lines carries the marker only on its first line, so
   the four posture attributes — `allowBackup`, `dataExtractionRules`, `usesCleartextTraffic`,
   `networkSecurityConfig` — were moved inside one comment, the file stayed well-formed, the
   `<application>` element lost all four, and both gates passed. That is F6 and F12 — two closed
   slices — removable with CI green.

2. `check-screen-security.sh` and `check-input-surfaces.sh` asserted presence with
   `grep -r <token> androidApp/src`. `androidApp/src/test` is inside that path. Deleting
   `FLAG_SECURE`, `filterTouchesWhenObscured`, `denyAutofillExport()` and `denyContentCapture()`
   from `MainActivity` and putting those strings in a `listOf` inside a test file — dead code that
   never runs — left both gates green. That is F1 and F3.

Neither was a regression. **Both had been blind since birth**, and the rule that should have caught
them — "cada gate se ensaya con archivo-cebo antes de confiar en su verde" (§9) — *was* followed.
It was followed by the person who had just written the gate, which is the weakness: the author of a
gate writes the bait their gate catches, because it is the same head. And the rehearsal was an act,
not an artifact, so nothing repeated it.

The same two techniques were found in four more gates: `check-preauth-surfaces.sh` (a real
declaration with a trailing comment on the same line was dropped by `grep -v '<!--'` entirely) and
`check-ios-host.sh` (its line-based Swift filter only recognised comments whose line *starts* with
a marker, so the inner lines of a `/* … */` block walked through).

## Decision

**1. A gate asserts the MECHANISM, not the token.** The pattern is the call or the assignment —
`window\.decorView\.filterTouchesWhenObscured[[:space:]]*=[[:space:]]*true`, not
`filterTouchesWhenObscured`. An `import` of the same name, a constant, a doc comment and a string
cannot satisfy a call-shaped pattern. The existing exception stands and is now explicit: where the
gate is an ALLOWLIST of values (ADR-0024's `importantForAutofill`), the assignment shape is what is
asserted, so an instrumented test may still NAME a forbidden constant to reproduce the OS.

**1b. Where the mechanism has a direction, the gate asserts the direction.** Bait caught a third
case while this ADR was being written: `check-ios-host.sh` required the presence of
`shouldAllowExtensionPointIdentifier` and stayed green when its body was changed to `return true`.
A hook that permits everything is the default with extra steps. The gate now requires the refusal.

**2. A gate asserts in the PLACE where the mechanism runs.** Presence checks are scoped to the
shipped path — `androidApp/src/main`, never `src/test` or `src/androidTest`. A control that only
exists in a test protects nobody.

**3. Comments and string literals are removed by a TOKENIZER, never by a line filter.**
`Scripts/lib/uncomment.py` blanks them in place (line numbers survive, so failure messages still
point at the real line), handles nested Kotlin and Swift block comments, and respects string
escapes. String literals are dropped for presence checks only; an absence check keeps them,
because a forbidden API named in a string is still worth a look.

**4. XML configuration is PARSED, not grepped.** `Scripts/lib/xmlattr.py` reads an attribute off a
named element. A parser cannot see a comment, so that whole class of bait becomes impossible by
construction rather than by a filter someone has to keep ahead of. It closes a second hole the grep
never covered: `android:allowBackup` on an `<activity>` is not the application's backup posture,
but it is the same text.

**5. The bait rehearsal becomes an artifact that RUNS.** `Scripts/rehearse-gates.sh` deletes or
disguises each control, in the spellings that actually fooled these gates, and requires the gate to
turn red. It runs in CI, last, after the gates it rehearses. Fifteen baits today.

A note on its own instrument, recorded because it is the same defect one level up: the first draft
of the rehearsal built a `git worktree` of HEAD while the fixes sat uncommitted, and reported
eleven undetected baits against gates that were already repaired. It now measures the working tree.

## Consequences

- Six gates were rewritten; the real code passes all of them and fifteen baits are caught.
- Two shared helpers exist under `Scripts/lib/`, the first shared code in `Scripts/`. The
  duplication they remove is the reason: the same defect appeared in six gates because the
  technique was copy-pasted, and a fix applied six times is a fix that will be applied five times
  next year.
- CI gains a step. It is cheap (seconds, ubuntu) and it is the only step that can fail because
  *another step cannot fail*.
- **What this does NOT fix.** The audit found the same family in gates outside this change:
  `check-data-boundary.sh` sees only `val`/`var` declarations and does not know the word «motivo»;
  `check-wrapper.sh` asserts `distributionSha256Sum` by presence, not by value; the CI action pin
  check accepts `@latest`; a package named `core` under `androidApp` disables `ForbiddenImport`.
  Each needs its own bait and its own fix. They are named here so that closing this ADR does not
  read as closing the class.
- The rehearsal is not proof of completeness. It catches the baits someone thought of — which is
  the same limit as before, with one difference that is the whole point: **it keeps catching them.**
