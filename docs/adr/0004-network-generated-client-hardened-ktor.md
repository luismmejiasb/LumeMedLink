# ADR-0004 — Networking: generated contract client over one hardened Ktor stack

- **Status:** Accepted · 2026-08-17
- **Related:** ADR-0004 de LumeMed and LumeNetworking's own constitution (the doctrine being
  mirrored); ADR-0027 de LumeMed (generated client, committed).

## Context

LumeMed's rule — all egress through one hardened stack, client generated from the versioned
contract, a breaking change breaks the build in CI — earned itself. The Swift kit cannot cross to
Kotlin; the doctrine can.

## Decision

- **One `HttpClient`** (Ktor), assembled once in the composition root: HTTPS-only fail-closed,
  TLS ≥ 1.2, no silent redirects, redacting logging (single log seam), bounded retry on GET only,
  RFC 9457 problem+json mapped to this app's own error model (auth-expired / retryable /
  validation / safe-user-message — the same taxonomy as LumeMed §7.2).
- **Client generated from the platform's `openapi.json`**, vendored and pinned by tag/commit; the
  exact generator (openapi-generator kotlin vs alternatives) and committed-vs-plugin output are
  decided at wiring time, with LumeMed's ADR-0027 (committed + reproduce-gate) as the precedent to
  beat.
- **Zero egress outside the stack**: no raw OkHttp/HttpURLConnection/NSURLSession, no third-party
  image loaders fetching on their own — profile photo bytes ride the stack, views receive bitmaps.
- Declared to the OS as well: Android `networkSecurityConfig` with cleartext off explicitly and
  system-only trust anchors; iOS ATS with no exceptions. **Corrected 2026-08-21 (ADR-0016): this
  bullet described a control that did not exist — no `networkSecurityConfig` and no
  `usesCleartextTraffic` were declared anywhere until F12 landed them.** The iOS half is still
  unverified: there is no iOS host to carry an Info.plist.
- **Pinning deferred — this repo's own decision, not an inherited one.** LumeMed's constitution
  mandates pinning through its kit's seam; what exists there besides the mandate is an unresolved
  audit candidate, not a deferral decision. The reasoning here stands on its own: pinning defends
  T5 (network attacker), the least likely level of this profile, and makes every certificate
  rotation a forced app release. Re-evaluated at production traffic.

## Consequences

- The detekt gate `no_raw_networking` (S0) is the enforcement; until it exists this is [manual].
- `Idempotency-Key` remains app+backend business; nothing here retries a POST.

## Amendment, 2026-08-21 (ADR-0017) — this decision stands, its stated reason does not

ADR-0017 re-evaluated the deferral and left this ADR standing, but two of the premises written here
no longer match the threat model, and a reader of this file alone would take away the wrong reason:

- **"Only T5, the least likely level."** On Android that branch is now closed by
  `networkSecurityConfig` (ADR-0016), and on iOS it is not a T5 branch at all: iOS trusts
  user-installed root CAs, so someone with access to the device installs a profile — that is T1/T2,
  the levels this app ranks first.
- **"Every certificate rotation is a forced release."** True of leaf/SPKI pinning; a pin on a
  public root does not have that property. The cost depends on what is pinned, and that depends on
  a backend certificate strategy nobody has decided yet.
- **"Re-evaluated at production traffic"** is replaced by the named trigger ADR-0017 chose: an iOS
  host exists AND the backend's certificate strategy is known.

Pinning is also not the only way to close the iOS gap — see ADR-0017 Part 2 for the trust-anchor
alternative and its unverified status.
