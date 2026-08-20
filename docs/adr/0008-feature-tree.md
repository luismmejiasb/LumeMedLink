# ADR-0008 — Organised by feature; platform source sets are edges, not layers

- **Status:** Accepted · 2026-08-17
- **Related:** ADR-0038 de LumeMed (the tree being mirrored, and the promotion rule).

## Context

LumeMed replaced its four technical layers with a feature tree (its ADR-0038) because the slice of
work is what the user does, and the folder became the gate. Same reasoning here, with one
platform-specific addition: KMP introduces `androidMain`/`iosMain` source sets, which look like
layers and must not become them.

## Decision

```
composeApp/src/commonMain/kotlin/…/
├── app/          # composition root + shell. The only code that sees everything, to wire it.
├── features/<área>/<pantalla>/   # XScreen · XViewModel · XModels · XUseCases
│   └── shared/   # what ≥2 screens of THAT area share — the area's single door
├── shared/       # pure value types: data classes, enums, domain errors. Sees nothing.
└── core/         # capabilities: networking, session, security, designkit, navigation…

androidMain/ · iosMain/   # ONLY `actual` implementations of `expect` seams declared in core/
```

- Direction: `features → core → shared`; `app/` wires everything and owns no domain.
- **`expect`/`actual` lives in `core/` only.** A feature never knows which platform it runs on; it
  talks to a `core/` interface whose `actual` sits in the platform source set. A platform `#if`
  inside a feature is the violation.
- **Promotion rule, verbatim from LumeMed**: what a second feature needs moves to `core/` in the
  same change, loses its origin screen's name, and its test moves with it. Downward is optional,
  upward is mandatory.
- Enforcement: a feature-isolation script mirroring LumeMed's (`Scripts/feature-isolation` on
  package names) lands in S0; until then **[manual]**.
