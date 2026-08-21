# PROGRESS — LumeMedLink

> El estado real, sin adornos. Si este archivo dice más de lo que el código hace, es un defecto (la
> regla de la familia: la doc nunca queda atrás — ni adelante — del código).

| Fase | Estado | Detalle |
| --- | --- | --- |
| Constitución + ADRs + threat model | ✅ 2026-08-17 | `CLAUDE.md`, ADR-0001…0008, `docs/security/threat-model.md`. |
| S0.1 (esqueleto KMP) | ✅ 2026-08-20 | `:composeApp` (KMP, árbol ADR-0008) + `:androidApp` (shell). `./gradlew clean build` y `compileKotlinIosSimulatorArm64` verdes en esta máquina, catálogo + lockfiles. **CI escrito pero jamás corrido: no hay push.** Bitácora 0002. |
| S0.2 (gates) | ⬜ | Siguiente. |
| S0.3 (design system) | ⏸️ | **No se toca**: espera el veredicto del autor sobre LumeUIComposer (WORKPLAN). |
| Fase 1 (lado médico) | ⬜ | Sin empezar. Bloqueada por S0. |
| Fase 2 (lado paciente) | 🔒 | **Gated por ADR-0006**: el backend debe aceptar el ADR del tier paciente primero. |

## Decisiones abiertas (autor)

- Nombre público de las tiendas (junto con el de LumeMed).
- Cuándo pedir al backend el ADR del tier paciente (S2.0).

Resueltas: la fila en `ECOSYSTEM-STATUS.md` — autorizada por el autor y agregada el 2026-08-20
(§3.1 del tablero, commit `f5fd53c` del backend).
