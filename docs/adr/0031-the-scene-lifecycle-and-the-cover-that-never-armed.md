# ADR-0031 — The scene lifecycle, and the cover that never armed

- **Status**: Accepted
- **Date**: 2026-09-21
- **Amends**: ADR-0025 (the iOS host) and ADR-0010 (screen-capture protection). Replaces the
  conclusion ADR-0028 withdrew, this time with a measurement instead of a retraction.

## Context

The iOS privacy cover — a `UIWindow` above `.alert`, the only defence against the snapshot iOS
takes when the app leaves the foreground, because there is no `FLAG_SECURE` — was armed in
`applicationWillResignActive`.

**It never armed. Not once, from the day the host was written on 2026-08-25.**

In an app that adopts the **UIScene** lifecycle, UIKit does not call four `UIApplicationDelegate`
methods: `applicationWillResignActive`, `applicationDidBecomeActive`,
`applicationDidEnterBackground` and `applicationWillEnterForeground`. A SwiftUI `App` with a
`WindowGroup` **is** scene-based — with or without `UIApplicationSceneManifest` in the plist — and
`@UIApplicationDelegateAdaptor` does not bring those methods back.

Measured, with a positive control in the same delegate:

```
LUMEPROBE didFinishLaunching                     <- control: fires
LUMEPROBE notif UIScene.willDeactivate           <- fires
LUMEPROBE notif UIApplication.willResignActive   <- the NOTIFICATION fires
(absent)  applicationWillResignActive            <- the METHOD does not
```

There was no error, no warning and no crash. Code that reads as a control, compiles, ships, and does
nothing — and a gate that **required** it, so the gate was enforcing dead code.

Three things made it survive three weeks of looking straight at it:

1. **`UIApplication.willResignActiveNotification` is still posted.** Only the delegate *method*
   stops being called. Anyone reasoning "the app finds out anyway" is half right, through a channel
   nobody was listening on.
2. **The Compose overlay covers the same pixels today**, so the host window's failure was invisible
   from the outside. Its job is what Compose *cannot* cover — an alert, a share sheet, a system
   prompt — and none of those exist in this app yet.
3. **Every attempt to measure it was confounded by something else**: first a stale Kotlin framework
   (ADR-0028), then a link that never refreshed (ADR-0030), then a switcher card that looks blank
   because the placeholder screens draw black text on no background.

`shouldAllowExtensionPointIdentifier` — the third-party keyboard veto, §8.10 — is **not** in the
replaced set and was working the whole time. The set is exactly those four.

## Decision

**1. The cover is armed from the scene notifications**, `UIScene.willDeactivateNotification` and
`UIScene.didActivateNotification`, observed in `didFinishLaunching`. `willDeactivate` is the scene
equivalent of `willResignActive` and fires before the snapshot; `didEnterBackground` is already too
late. The scene comes from the notification's object rather than
`UIApplication.shared.connectedScenes.first`, which was arbitrary ordering dressed as a lookup.

**2. The gate is inverted.** `check-ios-host.sh` required `applicationWillResignActive`; it now
requires the scene notifications and **fails if any of the four replaced methods is implemented at
all**. Naming them makes re-introducing one a decision rather than a habit. Two baits in
`rehearse-gates.sh`.

**3. F1 on iOS is closed, and it is closed by a measurement with a live control.**
`Scripts/verify-ios-privacy-cover.sh` reads the snapshot iOS actually writes —
`Library/SplashBoard/Snapshots/sceneID:<bundle>-default/*.ktx` — and compares sizes across three
conditions, because with two covers one can hide the other's failure:

| condition | largest scene snapshot |
|---|---|
| production, both covers | ~1.0–2.3 KB (flat) |
| **host window alone**, Compose overlay off | ~1.0–2.3 KB (flat) |
| **neither cover** — the live control | **7.3–10.6 KB (content)** |

Measured on two different simulators and two iOS versions. A KTX of a flat colour compresses to a
fraction of one carrying content, so the size IS the measurement; the file does not need decoding.

## Amendment — 2026-09-21: the cover does not pick a scene

The first version of this fix took the scene from the notification and fell back to
`UIApplication.shared.connectedScenes.first`. The LumeMed session named the rule its own code
carries for the same problem — *"a window on the wrong scene covers the wrong screen silently"* —
and the fallback is gone. It traded a **visible** absence for an **invisible** wrong screen, which
is the worse of the two, and phone-first means `first` would be right today and silently wrong the
day it is not.

The cover is now one window per scene, keyed by `session.persistentIdentifier`, created for every
connected window scene and released on `UIScene.didDisconnectNotification`. **There is no scene to
pick, so there is no pick to get wrong** — the same reasoning as their capture check, which asks
"is any screen captured" with `.contains` rather than asking which one.

Re-measured after the change, because changing a control invalidates the last verification of it:
`verify-ios-privacy-cover.sh` returns the same three numbers (1020 / 1020 / 7339).

And the reassuring row checked rather than assumed, since LumeMed found exactly this false on their
side — a single-scene decision their built `Info.plist` did not honour: **ours does.** The built
`.app` carries `UIDeviceFamily = {1}`, `LSRequiresIPhoneOS = true`, no `UIApplicationSceneManifest`,
and `TARGETED_DEVICE_FAMILY = 1` in both configurations. Multi-window is not reachable here, and the
cover above is correct regardless of that, which is the point of not depending on it.

## Consequences

- **Bitácora 0023's conclusion is now replaced, not just withdrawn.** It said the Simulator cannot
  verify this control, because the switcher card was blank with both covers disabled. The card was
  the wrong artifact. The file on disk is the right one, and the Simulator verifies it fine.
- **The exposure that existed was real but narrow**, and saying so honestly matters: the Compose
  overlay covered the Compose content the whole time, and there is no screen in this app today that
  presents anything above it. What was missing was the layer that will matter the moment a biometric
  prompt, an alert or a share sheet exists — which is F4's own end-to-end, one slice away.
- **A rule for this family, wider than this bug:** a security control that depends on a system
  callback needs a measurement proving the callback ARRIVES. Compiling is not evidence. An `NSLog`
  with a positive control costs one build.
- **LumeMed is not affected, and the reason is NOT the one this ADR first gave.** Corrected
  2026-09-21 by the LumeMed session, which measured its own tree instead of taking my grep: it
  implements none of the four methods (confirmed), but it does **not** observe `scenePhase` — its
  `ScreenSecurityMonitor` observes `UIApplication.willResignActiveNotification` through
  `NotificationCenter`. It is clean because it happens to sit on the good side of the same trap this
  ADR describes: the notification is still posted, only the delegate method stops being called.

  The correction matters more than the fact. **There are THREE states, not two**, and this ADR
  originally collapsed two of them:

  | mechanism | in a scene-based app |
  | --- | --- |
  | the four `UIApplicationDelegate` methods | **never called** |
  | the equivalent `NSNotification`s | **still posted** — what both apps now use |
  | `scenePhase` | alive, but LumeMed's ADR-0028 **forbids** deciding a cover from it: measured there to stop updating once the app settles, so the cover did not rise. "Un control de seguridad no espera un re-render." |

  So "use `scenePhase`, it is the SwiftUI-native way" — which is what this ADR implied by describing
  LumeMed that way — is advice the sibling app measured to be wrong for exactly this control. Left
  uncorrected it would have pushed the next reader toward the one option of the three that fails
  silently and late.
