# ADR-0002 — Kotlin Multiplatform + Compose Multiplatform, phone-first

- **Status:** Accepted · 2026-08-17
- **Related:** ADR-0008 (feature tree); LumeMed §0 (its platform decision, iPad-first Swift).

## Context

The audiences are patients and the doctor's pocket: this is a phone product. Patients in Chile are
majority Android; the doctors already carry iPhones for LumeMed. One codebase that ships both,
without duplicating the domain, is the point of choosing multiplatform. The author chose Kotlin
Multiplatform with Compose Multiplatform for the UI (stable on iOS since 2025).

## Decision

- **Kotlin Multiplatform**, targets `androidTarget` + `iosArm64`/`iosSimulatorArm64`.
- **UI in Compose Multiplatform**, shared in `commonMain`. Portrait, phone-first; tablet layouts are
  out of scope for v1.
- Platform APIs (Keystore/Keychain, BiometricPrompt/LAContext, FLAG_SECURE/privacy cover) enter
  through `expect`/`actual` seams that live in `core/` only (ADR-0008).
- **The Swift kits do not cross.** LumeUIKit, LumeNetworking and LumeFileManager are Swift and stay
  in LumeMed's world. What crosses is their **constitutions**: the networking doctrine is
  re-implemented over Ktor (ADR-0004), and the design language arrives through
  **`LumeUIComposer`** — the family's Compose twin of LumeUIKit (`../LumeUIComposer`), a separate
  repo whose own Slice 0 viability decision belongs to the author and is still open. Two honest
  postures until it lands: if the twin survives, this app consumes it like LumeMed consumes
  LumeUIKit (local path in dev, versioned later); if it is archived, this repo grows an internal
  `designkit` module with the ported tokens, shaped so its components can be promoted to a kit
  later. Either way: mirroring doctrine, never code across languages.

## Consequences

- Two app stores, one release train; CI must build both targets from day one (S0).
- Kotlin-side skills/gates equivalent to the Swift family's do not exist and must be built (detekt
  rules, S0) — until then every constitutional rule is [manual].
- The iOS side of this app follows Apple-platform facts (Keychain semantics, ATS) exactly as
  documented in the family's `lume-security` skill, which remains loadable for that half.
