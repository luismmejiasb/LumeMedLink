# PROGRESS — LumeMedLink

> El estado real, sin adornos. Si este archivo dice más de lo que el código hace, es un defecto (la
> regla de la familia: la doc nunca queda atrás — ni adelante — del código).

| Fase | Estado | Detalle |
| --- | --- | --- |
| Constitución + ADRs + threat model | ✅ 2026-08-17 | `CLAUDE.md`, ADR-0001…0008, `docs/security/threat-model.md`. **Sólo documentos: no existe una línea de Kotlin todavía.** |
| S0 (esqueleto, gates, designkit) | ⬜ | Sin empezar. |
| Fase 1 (lado médico) | ⬜ | Sin empezar. Bloqueada por S0. |
| Fase 2 (lado paciente) | 🔒 | **Gated por ADR-0006**: el backend debe aceptar el ADR del tier paciente primero. |

## Decisiones abiertas (autor)

- Nombre público de las tiendas (junto con el de LumeMed).
- Autorizar la fila de este repo en `ECOSYSTEM-STATUS.md` del backend (§1.1).
- Cuándo pedir al backend el ADR del tier paciente (S2.0).
