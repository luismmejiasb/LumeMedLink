# ADR-0039 — The shell's state outlives a configuration change, in memory only

- **Status**: Accepted (2026-10-07). §5 already decides MVVM with the state in a ViewModel; this records
  where the shell's state goes and the one dependency it needs.
- **Related**: ADR-0011 (the lock), ADR-0032 (the window), ADR-0034 (the attempt ceiling), task `0009`.

## Context

`MainActivity` declares no `configChanges`, so a rotation, a theme change or a font-size change recreates
the Activity, and with it the composition. The session state (`hasSession`, the inactivity window) lived in
`remember` inside `App()`: the app was reborn locked and the session re-probed. Fail-closed — not a hole —
but a biometric prompt for turning the phone.

## Decision

1. **A `ShellViewModel` holds the inactivity window and the probe's answer.** The lock built around the
   window is rebuilt by each composition, because the biometric gate holds the Activity it prompts in and a
   ViewModel holding it would leak the Activity it outlives.
2. **In memory only.** Surviving a rotation is not surviving the process: nothing here is persisted, so a
   killed process is still born locked (§8.3). The attempt count already lives in the store (ADR-0034).
3. **Not `android:configChanges`.** It would hide the symptom and leave security state hanging from a
   composition — the task's own "what not to do".
4. **One dependency**: `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` 2.9.6 — same group and
   version as `lifecycle-runtime-compose`, already allowlisted; the ViewModel runtime itself already shipped
   transitively.
5. **Gate**: `check-biometric-contract.sh` refuses an `InactivityLock(` built anywhere in production code
   but `ShellViewModel`, with a bait.

## Consequences

- Not observable end to end yet: without a login there is no session to rotate. The ViewModel's survival
  across a configuration change is the library's contract; the end-to-end check comes with S1.1, like F4's.
