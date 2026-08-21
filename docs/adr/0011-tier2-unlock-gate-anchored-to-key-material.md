# ADR-0011 — The tier-2 unlock gate: key material, never a boolean

- **Status:** Accepted · 2026-08-21 (fortification slice F4)
- **Related:** ADR-0005 (which pins the tier-2 key parameters as contract and left the mechanism to
  wiring time); ADR-0020 de LumeMed (cancel ≠ fail); threat model T1/T2.

## Context

ADR-0005 fixed WHAT the tier-2 gate must guarantee — authentication per use, strong biometrics
only, invalidation when the enrolled biometrics change — and said explicitly that those parameters
are contract, not implementation taste. It did not fix HOW the gate is built, nor how the app
decides what an unlock attempt means. F4 needs both, because the inactivity lock built in
`core/session` had nothing to unlock it.

The failure mode this ADR exists to prevent is the **boolean gate**: asking the platform "did this
person authenticate? yes/no" and letting a `true` open the app. That check is one patched branch
away from always passing, and it is the shape most biometric integrations take by default
(`LAContext.evaluatePolicy`, or a bare `BiometricPrompt` success callback with no crypto object).

## Decision

**Unlocking means recovering key material the operating system refuses to release without a fresh
biometric match.** Never a boolean, on either platform.

- **Android** — an **EC P-256 key pair inside AndroidKeyStore**. Unlock = signing a stored random
  challenge through `BiometricPrompt`'s `CryptoObject` and verifying the signature against the
  key's public half. Only the hardware-held private key can produce it, and the OS will not release
  it without the prompt succeeding. Key parameters (gated by
  `Scripts/check-biometric-contract.sh`): `setUserAuthenticationRequired(true)`, per-use
  authentication (`setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)` on API 30+,
  `setUserAuthenticationValidityDurationSeconds(-1)` below it), and
  `setInvalidatedByBiometricEnrollment(true)`. No `DEVICE_CREDENTIAL` anywhere.
- **iOS** — a **Keychain item whose access control is `.biometryCurrentSet`**, holding 32 random
  bytes. Reading it IS the prompt: the Secure Enclave evaluates the ACL before returning any data.
  The accessibility class rides inside the access-control object at the ADR-0005 floor
  (`WhenPasscodeSetThisDeviceOnly`), because `kSecAttrAccessible` and `kSecAttrAccessControl` are
  mutually exclusive.
- **`androidx.biometric` 1.1.0** (newest stable; 1.4.0 is alpha and this family ships no alphas in
  a security path) rather than the framework `BiometricPrompt`, which starts at API 28 while minSdk
  is 26 — the compat library is what keeps the oldest supported devices on a real gate instead of a
  silent downgrade. It requires the shell to be a `FragmentActivity`.
- **The policy lives in `SessionLock`**, in common code, so every branch is pinned by a test:
  cancelling costs nothing; wrong biometrics count and enough of them end the session; a changed
  enrollment or absent biometrics end the session immediately.

## Consequences

- **Fail-closed everywhere.** No gate, no biometrics, changed enrollment, or too many misses all
  end the session and return to Login — never a quietly open app. An absent platform gate resolves
  to `AbsentUnlockGate`, which reports `Unavailable`; a missing gate is never an open door.
- **The doctor is logged out when they enroll a new fingerprint.** Accepted, and it is the point:
  the alternative is that whoever holds the phone enrolls their own finger and inherits the session.
- **Verified where it matters.** The instrumented test `UnlockKeyContractTest` asks a real Android
  runtime, through `KeyInfo`, whether the four properties actually took — the grep gate proves the
  source says the right words, `KeyInfo` proves the OS agreed. iOS has no equivalent until the iOS
  host exists (the hostless K/N runner reaches no keychain).
- **Supply-chain cost, declared:** `androidx.biometric:biometric:1.1.0` drags eight transitive
  dependencies into the shell, including `appcompat:1.2.0` (2020). Accepted for now as the price of
  a correct compat matrix; re-evaluated in F20 together with the fact that the dependency allowlist
  currently admits any `androidx.*` group by prefix and so did not stop to ask.
- Cancelling is not a failed attempt, so a locked phone can be dismissed indefinitely without
  ending the session. That is deliberate (ADR-0020's regression); the session still dies on the
  inactivity/expiry paths, not on the prompt count.
