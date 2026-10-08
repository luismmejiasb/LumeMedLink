# ADR-0040 — A platform that cannot ask now costs nothing and ends nothing

- **Status**: Accepted — the author's decision of 2026-10-07 (task `0019`, option B).
- **Related**: ADR-0011 (the tier-2 gate), ADR-0034 (the attempt ceiling), ADR-0020 of LumeMed (dismissing is
  not a failure), task `0019`.

## Context

Three situations nobody causes were treated as if somebody had:

- a biometric prompt that **expires untouched** (Android `ERROR_TIMEOUT`) counted as a failed attempt;
- a **busy or temporarily unavailable sensor** (Android `ERROR_HW_UNAVAILABLE`, and the same answer from the
  check before the prompt) **ended the session with a full wipe**;
- the **phone locking while the tier-2 item was read** (iOS `errSecInteractionNotAllowed`, `errSecNotAvailable`)
  ended the session too — while on Android the same event already arrived as a free cancel.

## Decision

A new outcome, `UnlockOutcome.NotNow`: the app **stays locked, the attempt is not counted, the session does not
end**, nothing is reported, and the person tries again. Mapped from exactly the situations above; nothing else.

- No hardware, nothing enrolled, a pending security update: still `Unavailable` — re-entry is impossible, not
  postponed (and ADR-0037's amendment decides what a phone with no hardware gets instead).
- `ERROR_UNABLE_TO_PROCESS` and anything unclassified: still a failed attempt. The rule that what nobody
  classified counts against an attacker is unchanged.

## Why it is safe

`NotNow` opens nothing. The door stays shut and still needs a matching biometric; an attacker who makes the
sensor busy or lets the prompt expire gains exactly what dismissing the prompt already gave — nothing.

## Consequences

Pinned by `PromptErrorMappingTest` (Android, including the check before the prompt), `KeychainStatusMappingTest`
(iOS) and `SessionLockTest` (twenty `NotNow` in a row: still locked, nothing spent, nothing reported).
