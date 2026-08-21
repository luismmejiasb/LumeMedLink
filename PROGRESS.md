# PROGRESS — LumeMedLink

> El estado real, sin adornos. Si este archivo dice más de lo que el código hace, es un defecto (la
> regla de la familia: la doc nunca queda atrás — ni adelante — del código).

| Fase | Estado | Detalle |
| --- | --- | --- |
| Constitución + ADRs + threat model | ✅ 2026-08-17 | `CLAUDE.md`, ADR-0001…0008, `docs/security/threat-model.md`. |
| S0.1 (esqueleto KMP) | ✅ 2026-08-20 | `:composeApp` (KMP, árbol ADR-0008) + `:androidApp` (shell). `./gradlew clean build` y `compileKotlinIosSimulatorArm64` verdes en esta máquina, catálogo + lockfiles. **CI escrito, jamás corrido, y PAUSADO a `workflow_dispatch` (2026-08-21): la cuenta llegó al 90% de sus minutos de Actions y macOS factura 10×** — se dispara a mano desde la pestaña Actions cuando el autor decida; los gates quedaron en job ubuntu (1×) separado del build macOS. Bitácora 0002. |
| S0.2 (gates) | ✅ 2026-08-20 | detekt + ktlint + 3 scripts (`Scripts/`) + allowlist de dependencias, **cada gate visto rojo con cebo** (el ensayo atrapó un gate ciego de nacimiento). `no_mutable_object`/`no_hardcoded_style` siguen [manual], declarado. Daemon JVM pinneado a 17. Bitácora 0003. |
| Espejo de red (`core/networking`) | ✅ 2026-08-20 | ADR-0004 sobre Ktor: https-only fail-closed ×2, sin redirects, retry sólo-GET acotado, RFC 9457 → `AppError` sin prosa del servidor, seams `TokenProvider`/`NetworkLogSink`, engines OkHttp (TLS≥1.2 explícito)/Darwin. **13 tests × 2 targets verdes** (Android host + simulador iOS). Bitácora 0004. |
| S0.3 (design system) | ⏸️ | **No se toca**: espera el veredicto del autor sobre LumeUIComposer (WORKPLAN). |
| Fase 1 (lado médico) | ⬜ | Sin empezar. Bloqueada por S0. |
| Fase 2 (lado paciente) | 🔒 | **Gated por ADR-0006**: el backend debe aceptar el ADR del tier paciente primero. |

## Decisiones abiertas (autor)

- Nombre público de las tiendas (junto con el de LumeMed).
- Cuándo pedir al backend el ADR del tier paciente (S2.0).

Resueltas: la fila en `ECOSYSTEM-STATUS.md` — autorizada por el autor y agregada el 2026-08-20
(§3.1 del tablero, commit `f5fd53c` del backend).
