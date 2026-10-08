# ADR-0042 — What accessibility and the clipboard may carry

- **Status**: Accepted — the author's decisions of 2026-10-07 (tasks `0021` and `0022`, option B in both).
- **Related**: ADR-0013 (clipboard and keyboard, amended 2026-10-06), ADR-0024 (structure export), ADR-0033 (the
  kit), §8.9 of the constitution.

## Decision

1. **Accessibility (Android 14+).** The personal data a person types is marked sensitive for accessibility, so
   only services that declare themselves accessibility tools (TalkBack) can read it. **Labels, placeholders and
   guidance text are not marked**: they carry no personal data and every service may read them.
2. **The iOS clipboard inside fields.** What is copied or cut inside a field goes to the pasteboard **local only**
   (no Universal Clipboard to the person's other Apple devices) and **expires after 2 minutes**. Pasting keeps
   working. Android has no equivalent API and stays as ADR-0013 declares it.

## Why both wait on the kit

Measured on 2026-10-07 (API 37 emulator, UiAutomation, with a positive control): the semantics mark passed as the
kit field's `modifier` lands on the field's root and **does not reach the editable node**; a node of ours marked
directly reads sensitive, so the platform and Compose do their part. Only the kit can mark the value's node, and
only the kit decides how its fields copy. Both were requested from LumeUIComposer the same day.

`Scripts/verify-accessibility-sensitivity.sh` asserts the three things that must hold — the control seen, the value
marked, the label not marked — and is **red today for exactly the second**. It turns green when the kit lands.

## Declared

- Android 13 and below: the mark does not exist; accessibility services read everything, as before.
- A service that falsely declares itself an accessibility tool still reads the value; Play policy, not the app,
  polices that claim.

## Confirmed — 2026-10-07

The author read back every interpretation written in this ADR (what counts as "not now", "a different phone", both roles, the full wipe on the old phone, close-then-warn, accessibility tools still reading the value, the 2-minute expiry, "no capability" meaning no hardware, both codes) and confirmed them as meant.

## Landed — 2026-10-07 (accessibility)

The kit's `LumeFieldSensitivity` marks the value's node only; `SensitiveTextField` asks for `Personal` on every field.
`Scripts/verify-accessibility-sensitivity.sh` is green on the API 37 emulator, control seen: value nodes sensitive,
labels and guidance not. The clipboard half still waits on the kit's task 0023.

