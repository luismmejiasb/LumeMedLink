# ADR-0010 — Screen-capture protection is app-wide, and the platform asymmetry is the design

- **Status:** Accepted · 2026-08-21 (fortification slice F1)
- **Related:** threat model T2; §8.3 of this constitution; LumeMed ADR-0023/0028 (the iOS cover, no
  fake blackout); ADR-0005 (the platform-asymmetry discipline this follows).

## Context

Threat model T2 (shared device) is this app's first-ranked threat: a patient's phone is used by the
family, and an appointment with a named specialist visible on screen — or in the OS app-switcher
snapshot, or a screenshot — leaks health information (Ley 21.719). The two platforms do NOT offer
the same defense, and pretending they do protects neither (ADR-0005's discipline).

## Decision

**Screenshot and app-switcher protection is APP-WIDE, not per-screen.** The constitution's §8.3
says "every window with personal data"; for this app that is every window, because the whole
surface is personal-data-adjacent — the agenda reveals health, the contacts are a patient roster.
App-wide is therefore both the correct security posture and the simplest to enforce (no per-screen
audit that rots).

Per platform, the asymmetry IS the design:

- **Android — `FLAG_SECURE`, set on the Activity window.** Android *can* block screenshots and
  blank the recents thumbnail; here it is a hard rule, applied once at the shell's entry point so
  no screen can forget it. Plus `filterTouchesWhenObscured` on the decor view: touches delivered
  while another window overlays ours are dropped (tapjacking).
- **iOS — a privacy cover, because there is NO screenshot API.** iOS does not let an app block a
  user's screenshot at all (asymmetry 1 of the threat model). The control is to COVER the content
  before the OS snapshots it for the app switcher. This slice builds the first layer: a Compose
  overlay driven by the lifecycle, shown whenever the app is not RESUMED. **Declared deferral:** the
  robust cover is a host `UIWindow` shown on the scene's `willResignActive`, which needs the iOS
  shell (Xcode host) that does not exist yet. The Compose overlay is the layer available without it.

No fake blackout on iOS (LumeMed ADR-0023 retired that): the cover is a real opaque surface, not a
pretended one.

## Consequences

- The user cannot screenshot ANY screen on Android — accepted for a clinical-adjacent app.
- Runtime proof (the thumbnail is actually blank, the cover actually appears) is a **device check**
  in F-later/S1.2, not a host-test assertion — same honesty as the rest of the repo. What IS tested
  now: the cover's state logic (`shouldCover`) across every lifecycle state, on both targets.
- The gate `Scripts/check-screen-security.sh` fails if FLAG_SECURE is absent or cleared, or if a
  screen-capture API appears in app code — so this decision cannot silently rot.
- The privacy cover's color is a functional security primitive, not a design token; it carries a
  minimal neutral fill until the design system (S0.3) provides a theme surface.
