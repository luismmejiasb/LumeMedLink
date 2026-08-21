# ADR-0013 — Clipboard and keyboard: one choke point, and two asymmetries told honestly

- **Status:** Accepted · 2026-08-21 (fortification slice F3)
- **Related:** §8.9 (clipboard) and §8.10 (keyboard) of this constitution; threat model T4
  (passive platform exfiltration); ADR-0005's discipline of declaring platform differences.

## Context

Two ways a personal datum leaves this app **without a single network call**:

- **The clipboard is shared with every app on the device.** A copied RUT or phone number is
  readable by whatever else is installed, and on Android 13+ the system also renders the copied
  content in an overlay for anyone watching the screen.
- **The keyboard sees every keystroke.** A third-party IME can learn a value and later suggest it
  in another app — the leak is the *suggestion*, not the typing.

No text field exists in this app yet, which is why the decision belongs here: the first screen with
an input would otherwise make it by omission.

## Decision

**1. The app offers no copying.** No clipboard API has a caller, and
`Scripts/check-input-surfaces.sh` refuses one — including `SelectionContainer`, the quiet form of
the same leak (selectable text hands the rendered value to the system copy toolbar, and from there
to the same shared clipboard). When a feature genuinely needs copy, it arrives with its own
reviewed seam and its own ADR amendment, not as a convenience inside a screen.

**2. All sensitive input goes through one primitive**, `core/input/SensitiveTextField`, and the
gate fails the build on a raw `BasicTextField`/`TextField` anywhere else. Keyboard hardening is a
list of small attributes; a per-screen rule fails on the one screen that forgets, and that screen is
the one that leaks a RUT into a keyboard dictionary. One choke point also means that the day a
platform exposes an attribute we cannot reach today, it lands in a single file.

**3. The hardening is purpose-aware, not blanket.** `CREDENTIAL` fields use the password keyboard
and masking — IMEs are required not to learn from a password field — while still being fillable by
a password manager, because manager-generated passwords beat memorized ones and blanket-disabling
autofill would *lower* security. `PERSONAL_DATA` fields keep the keyboard type they need to be
usable (a phone keypad for a phone) but never get autocorrect or capitalization suggestions.
Hardening that makes fields unusable gets routed around, so usability here is a security property.

## The two asymmetries, declared rather than smoothed over

- **The clipboard.** iOS can mark a clip `.localOnly` with an expiration; **Android has no
  equivalent** — no API excludes a clip from cross-device sync or expires it. The best Android
  offers is `ClipDescription.EXTRA_IS_SENSITIVE` (API 33), which only redacts the preview overlay.
  So the mitigations are not comparable, and the honest Android mitigation is the one taken here:
  **do not offer copying.**
- **The keyboard.** iOS can refuse third-party keyboards app-wide via the app delegate;
  **Android cannot** — there is no API to veto an IME. The Android mitigation is per-field and
  partial, and saying otherwise would claim a protection that does not exist. The iOS veto itself
  is **not implemented yet**: it needs the iOS host (the Xcode project) that does not exist.

## Consequences

- Users cannot copy a phone number out of this app. Accepted: the phone number is one tap from the
  dialer in a future slice, which is the safe path anyway.
- **What the primitive does not do yet, listed so it is not mistaken for done:** Android's
  `IME_FLAG_NO_PERSONALIZED_LEARNING` and explicit autofill exclusion are not reachable from common
  Compose at the pinned version, and the iOS keyboard veto waits on the host. Each lands in the
  primitive (or the host) when it becomes reachable; none is claimed today.
- The primitive is deliberately unstyled — S0.3 dresses it, the security attributes stay.
- Verification is by test on the pure decision functions plus the gate, both rehearsed. A real
  keyboard's behaviour on a device is not asserted by anything here and is not claimed.
