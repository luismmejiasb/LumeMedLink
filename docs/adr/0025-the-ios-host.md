# ADR-0025 — The iOS host: what the shell owns, and what it proved by existing

- **Status:** Accepted · 2026-08-25 (unblocks F7 and F17; closes the iOS tails of F1 and F3)
- **Related:** ADR-0002 (Compose Multiplatform, phone-first), ADR-0008 (the tree — `app/` composes),
  ADR-0010 (screen capture), ADR-0013 (§8.10 keyboard), ADR-0007/F19 (no document delivery),
  §7 (transport), §8.12 (deep links), ADR-0001 (the data boundary — which is why the keychain group
  is not shared).

## Context

Six items of the fortification list were blocked on the same sentence: *there is no iOS host.*
`:composeApp` compiled for iOS and its Kotlin was tested, but nothing ran, and every iOS security
claim in this repo was **written and never executed**. F7 (install sentinel) and F17 (deep links)
could not begin; the iOS tails of F1 (window-level privacy cover) and F3 (third-party keyboard
veto) were declared owed; and no iOS behaviour could be verified at all.

`iosApp/` is one folder in this repository, beside `androidApp/`. Not a second repo and not a second
GitHub project: Kotlin Multiplatform is one repo with several targets, and the Xcode project
consumes the framework Gradle already produces.

## Decision

**1. The host hosts; it does not implement.** Everything shown comes from `commonMain` through
`MainViewController()`. The Swift is under 100 lines and contains only what Compose cannot reach:
the window, the app lifecycle, and the UIKit-level decisions the constitution assigns to the host.

**2. The two declared tails land here, because here is the only place they exist.**
- The **third-party keyboard veto** (`shouldAllowExtensionPointIdentifier`). This is the one
  asymmetry in iOS's favour that §8.10 names: a custom keyboard is a process that sees every
  keystroke, iOS lets an app refuse them, and **Android has no equivalent**. For an app whose
  fields carry a RUT, a phone number and a credential, the trade is the easy direction.
- The **privacy cover in its own `UIWindow`**, shown on `willResignActive`. That event is the last
  moment *before* iOS takes the snapshot the app switcher shows; `didEnterBackground` is already
  too late. Its own window rather than a view inside the app's, because a view can be covered,
  reordered or removed by whatever is presented on top — an alert, a system prompt — while a window
  above `.alert` is above all of it, including anything Compose presents.

**3. The keychain access group is the app's own, and never a shared one.** A shared group is
readable by every app signed by the same team — and LumeMed, which holds the clinical record, is
signed by the same team. Sharing a keychain group between them would dissolve, in one plist entry,
the boundary this repository exists for (ADR-0001).

**4. Three keys are ABSENT from `Info.plist` on purpose, and the absence is the control.** No
`NSAppTransportSecurity` (§7 says ATS without exceptions; the way to assert that is to have nothing
to read), no `UIFileSharingEnabled`/`LSSupportsOpeningDocumentsInPlace` (either turns the container
into a Files.app share point — ADR-0007), no `CFBundleURLTypes` (any app can claim a custom scheme;
§8.12 admits verified universal links or nothing).

**5. `Scripts/check-ios-host.sh` in the same change.** Every other gate scans
`composeApp/src androidApp/src`; the moment this host landed it was a directory full of
security-relevant decisions that **no gate could see** — a blind spot created by the change that
created the host. Twelve baits, plus the inverse control that a comment naming a forbidden API does
not trip it.

## Consequences — including what the host proved by existing

**It found a launch crash on its first run.** `App()` probed the session inside a `LaunchedEffect`
and let the exception escape: the Keychain answered an error, `KeychainSecureStore.get` threw —
correctly — and the process died at startup. The observed trigger was my own unsigned build, which
is **not** a production condition, and that is stated rather than dressed up. The *class* is
production-reachable and the store's own KDoc already names one instance: with
`WhenPasscodeSetThisDeviceOnly` the data-protection keychain answers `errSecNotAvailable` before the
device is first unlocked. Fixed in `app/probeSession`, outside the composable so it can be asserted,
with three tests and three baits: an unreadable store resolves to **no session** (the safe
direction — it costs a sign-in, never grants one) and is **recorded** through the
`SECURE_STORE_UNREADABLE` vocabulary that F22/F23 defined and nothing had ever called; a
cancellation is re-thrown rather than swallowed; and an ordinary empty store reports nothing.

**CORRECTION (2026-09-07, ADR-0028): the paragraph below was measured with a broken build and is
withdrawn.** The control disabled the cover by patching Kotlin, and this host was linking a stale
framework because its build phase never set `KOTLIN_FRAMEWORK_BUILD_TYPE` — so the patch never
reached the binary and "nothing changed" because nothing was changed. Re-measuring with the fixed
build was inconclusive, so the claim is retired rather than reversed. What stands: the cover is
still unverified.

~~**The iOS privacy cover is still NOT verified, and the Simulator cannot verify it.**~~ Three methods
were tried: the on-disk `SplashBoard` snapshots, their compressibility, and a screenshot of the app
switcher. All three returned the same result **with both covers disabled** as with both enabled —
the positive control could not be made to fail. The likely cause is that Compose renders through
Metal and the system snapshot does not capture that layer on the Simulator. Recorded so the next
session does not spend the same hours: **this control needs a real device.**

**The entitlement is written and, on an ad-hoc simulator build, not in effect.** `codesign -d`
reports an empty entitlement dictionary because no development team is set, so
`$(AppIdentifierPrefix)` does not resolve. It takes effect on a build signed with a team. Said here
because a file that exists is not a control that runs.

**Unblocked, not done:** F7 and F17 can now be built. They are their own slices.

---

## Cierre — 2026-09-21 (ADR-0031)

Lo que esta ADR dejó abierto queda cerrado, y en la dirección incómoda.

- **El cover que esta ADR describe, armado en `applicationWillResignActive`, NUNCA ARMÓ.** En una
  app de escenas —y una app SwiftUI lo es— UIKit no llama ese método. Medido con control
  positivo. Era código muerto desde el día que se escribió este host, y el gate lo EXIGÍA.
- **La afirmación retirada sobre el simulador queda reemplazada, no sólo retirada.** El
  simulador sí puede verificarlo: el artefacto no es la tarjeta del conmutador sino el archivo
  que iOS escribe en el contenedor. Con control en vivo, y en dos simuladores distintos.
- **Lo que esta ADR acertó y conviene subrayar**: que el cover viva en su propia `UIWindow` es
  correcto y sigue siendo la razón de que exista. Lo que estaba mal era el evento que lo armaba.
