# ADR-0062 — Debug builds reach the local backend over plain http, and only it

- **Status**: Accepted (2026-10-14), the author's decision by form («Excepción sólo en debug»).
- **Amends**: ADR-0016 (transport hardening) and §7 (https only, fail closed), for debug builds and the local
  backend only. Release is unchanged.
- **Related**: ADR-0004, ADR-0058 point 5, ADR-0061 point 5 (the dev route), task `0036`.

## Context

The platform is deployed nowhere: it runs on the author's machine, over `http://localhost:3000`, with a dev token in
place of Identity Platform (the platform's `docs/runbooks/local-dev.md`). This app's stack refuses anything but https,
and fails closed; so does Android's network security config, and iOS's ATS does by default. Wiring the app against the
platform needs one honest place where plain http is allowed, and it must not be able to reach a release build.

## Decision

1. **Only debug builds, only the local backend.** Plain http is allowed toward a closed set of local hosts:
   `localhost`, `127.0.0.1` and `10.0.2.2` (the Android emulator's name for the computer it runs on). A physical
   Android phone reaches the computer through `adb reverse tcp:3000 tcp:3000`, as `localhost`. No LAN address is
   allowed, so that no build can talk plain http across a network.
2. **The stack decides it from a policy, never from the URL.** `lumeHttpClient` keeps refusing `http` unless it is
   handed `TransportPolicy.LocalCleartext`, and even then only toward one of those hosts. A typo in a URL cannot open
   cleartext.
3. **The policy comes from the host app, by build type**, never from shared code:
   - Android: the environment is chosen in `androidApp/src/debug` and `androidApp/src/release`, two files. Release's
     file has no local URL and cannot build one.
   - iOS: the Swift host passes it under `#if DEBUG`, so a Release build does not compile it.
   - Without an environment, the app runs on its script, as today (ADR-0058 point 5).
4. **The platforms allow it the same way:**
   - Android: a `network_security_config` in `androidApp/src/debug/res/xml/` replaces main's in the debug variant, and
     permits cleartext toward those three hosts only. Main's file is unchanged: it still denies cleartext, trusts
     only system anchors, and has no `<debug-overrides>`.
   - iOS: `NSAllowsLocalNetworking` is written into the built app's Info.plist by a build phase that runs only in the
     Debug configuration. The source Info.plist keeps no ATS key.
5. **The dev token lives in memory and only behind the local environment.** `DevTokenProvider` asks the backend's
   `POST /dev/token` (described in `contract/dev-auth.json`, ADR-0061 point 5) for the `dev-patient` token. It keeps
   the token in memory, never on disk, and forgets it when the stack reports it rejected. Only the local environment
   builds it. Its code ships dark in release, because shared code cannot be split by build type. It is unreachable
   there, and the backend answers 404 to that route outside dev mode.
6. **Gates.** `check-network-posture.sh` refuses:
   - cleartext in main's config or in a release one;
   - a debug config that allows any host outside the three;
   - an environment with a local URL in Android's release source set.

   `check-ios-host.sh` refuses an ATS key in the source Info.plist, and the build phase without its
   `CONFIGURATION = Debug` guard. Each of these has its bait in `rehearse-gates.sh`.

## Consequences

- Traffic to the local backend is readable on the computer's loopback. It carries the dev token and synthetic data
  from the platform's seed, never a real person (§9).
- When a staging environment with https exists, debug builds point at it with `HttpsOnly`, and this exception becomes
  dead. It is retired then, with its gates' baits.
- Proxy debugging (Charles, mitmproxy) stays impossible on every build, as ADR-0016 decided. This ADR allows
  cleartext toward a local host; it does not trust any user CA.
