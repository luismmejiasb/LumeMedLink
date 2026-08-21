# ADR-0017 — URLs carry no personal data; and pinning, re-evaluated but NOT re-decided

- **Status:** Accepted for the URL half · 2026-08-21 (fortification slice F13).
  **The pinning half is an OPEN DECISION for the author** — see below. ADR-0004 stands until the
  author says otherwise; this ADR does not derogate it.
- **Related:** ADR-0004 (pinning deferred); ADR-0016 (the fourth asymmetry that reopened the
  question); §7 and §8.1; threat model T5.

## Part 1 — Nothing personal in a URL (decided)

### Context

A request body is read by the server and nothing else. A URL is copied, by default and without
anyone choosing it, into: the server's own access log with its query string; every proxy, load
balancer and CDN in between, and their logs; this app's log line; and exception messages, which
travel to crash reporters — a leak this repo has already had (ADR-0016 §4). A RUT in a query string
is a RUT written to half a dozen places nobody audited; the same RUT in a body is written to one.

The backend already made this call for its RUT search — it takes the RUT in the **body**, never the
URL. This keeps the app on the same side of that line.

### Decision

**No personal datum is ever placed in a URL.** Identifiers in the PATH are a different case and are
allowed: they are opaque server-side ids, not personal data standing alone, and the log redactor
replaces them regardless.

`Scripts/check-url-hygiene.sh` gates it over production source sets: personal-data field names as
query parameters, literal query strings naming them, and hand-assembled query strings (which escape
both the gate and the stack's encoding). Rehearsed against four bad shapes and two legitimate ones,
so it fails on the leak and stays quiet on `professionalId`/`from`/`to`.

**Test source sets are excluded, deliberately.** A test proving the redactor removes a RUT from a
URL has to build a URL with a RUT in it; a gate that goes red on the evidence of its own defense is
a gate the next person in a hurry disables.

## Part 2 — Pinning: what changed, and why this ADR does not decide it

### What ADR-0004 said, and what has changed under it

ADR-0004 deferred pinning with this reasoning: it "attacks only T5 — the least likely level of this
threat profile; against T6 it defends nothing — and turns every certificate rotation into a forced
release."

**Two of those inputs have changed:**

1. **ADR-0016 established that iOS trusts user-installed root CAs while Android now does not.** So
   on iOS this is no longer only a T5 (network attacker) question — it is a T1/T2 question, the
   levels this app ranks *first*: someone with access to the device installs a configuration
   profile, and the app's TLS is readable. Pinning is the only thing that closes it.
2. **A fifth asymmetry, reported by the F13 investigation and NOT verified by this session:** Apple
   enforces Certificate Transparency platform-wide for publicly-trusted certificates, and Android
   does not — which cuts the other way and makes Android the weaker platform against an attacker
   who obtains a mis-issued certificate from a public CA. Recorded with its confidence marked.

### What has NOT changed: pinning is not implementable today

- **There is no production certificate.** No GCP project, no staging, no deployed backend. There is
  nothing to pin.
- **There is no iOS host.** No Xcode project, no Info.plist — so on iOS there is nowhere to put a
  pin, and iOS is where the entire benefit would be.

### The middle option worth evaluating, with its caveat stated

The F13 investigation found that iOS **can** distinguish a chain validated against a system anchor
from one validated against a user-installed root: `kSecTrustResultUnspecified` (4) versus
`kSecTrustResultProceed` (1), reportedly with a second discriminator in the Certificate
Transparency result key. If that holds, a Darwin `handleChallenge` could give iOS the same
property Android now has — reject user-installed CAs — **without** pinning's rotation risk, and
**before** any certificate exists.

**Why this ADR does not implement it:** the evidence is simulator-only and was produced by an
investigation, not by this session. Nobody has confirmed that a configuration-profile CA on a
physical device, or an MDM-pushed CA on a supervised one, actually reports `Proceed`. Shipping a
TLS trust decision on unverified evidence, into a repo with no host to verify it in, would be the
exact failure this program keeps finding: a control that looks correct and is never measured. A
wrong handler either breaks all traffic or accepts everything.

### The recommendation, for the author to accept or reject

1. **Do not implement pinning now** — it is not possible.
2. **Stop citing ADR-0004's current rationale**, because its premise no longer matches the threat
   model. That is a doc-truth issue independent of the decision itself.
3. **When the iOS host exists, evaluate the system-anchor check first** — it is cheaper than
   pinning, closes the asymmetry, and needs no certificate. Its precondition is a real device test
   with a profile-installed CA, both states.
4. **Ask the backend, now, how its certificates will be managed** (provider-managed with rotating
   keys, or a pinnable key the app can survive). It has long lead time and it decides whether
   pinning is operable at all. That belongs in `docs/backend-requests/`.

## Consequences

- The URL gate is in CI. The pinning question is registered as an open author decision in
  `PROGRESS.md`, not silently left in a bitácora.
- **Not verified by this session:** the fifth asymmetry, and everything about the iOS system-anchor
  mechanism. Both are marked as reported.
- The URL gate sees NAMES, never values. A personal datum passed under a neutral parameter name is
  invisible to it — that is a real limit and the reason the doctrine is written down as well as
  gated.
