# ADR-0065 — A UI-test tour pictures the screens

- **Status**: Accepted (2026-10-14), the author's decision by form.
- **Related**: §8.3 (FLAG_SECURE), §9 (synthetic data only), ADR-0062 (the local platform), ADR-0052 (what the host
  may link), task `0037`.

## Context

The author asked for a picture of every wired screen. Android cannot give one: every window with personal data is
`FLAG_SECURE`, which is the control, and the emulator's screenshots come out empty. The iOS simulator can, but nothing
in the repo could drive it. Installing a driver such as `idb` is a global install, which this machine's rules forbid.

## Decision

1. **An XCUITest target, `iosAppUITests`, with one test, `ScreenTour`.** It signs in through the script, walks the
   patient's wired screens and saves a screenshot of each. A step that cannot find its element still saves what was on
   screen, and the tour goes on.
2. **`Scripts/tour-screens.sh` runs it.** It uses the repo's own simulator, picked by name, enrols a face, and installs
   fresh. The pictures go outside the repository, to `../tmp/screens-<time>`.
3. **It drives a Debug build**, so its data is the local platform's synthetic seed or the design's script, never a
   person's (§9). Android's `FLAG_SECURE` is untouched.
4. **The bundle links XCTest and nothing else**, and the host may not link XCTest. `check-ios-host.sh` enforces both,
   with a bait for each.

## Consequences

- The tour is not in CI and is not a test of behaviour: it pictures, it does not assert. A failed step is a picture
  named `…-sin-<label>`, and the log prints the accessibility tree for that step.
- Compose Multiplatform exposes its semantics to XCUITest. A button's text comes as a static text that is not
  "hittable", so the tour touches each element's middle by coordinate.
- The tour finds elements by their Spanish labels. When a label changes, its step says so in the picture's name.
