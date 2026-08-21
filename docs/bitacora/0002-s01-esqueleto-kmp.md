# 0002 · S0.1: el esqueleto KMP compila en los dos lados

**Fecha:** 2026-08-20 · **Encargo del autor:** ejecutar el handoff (`docs/HANDOFF-S0.md`) en su
orden: S0.1 → S0.2 → espejo de red. Esta bitácora cubre S0.1.

## Qué existe ahora

Dos módulos, calcados de la topología que LumeUIComposer ya pagó con AGP 9:

- **`:composeApp`** — el módulo KMP (`com.android.kotlin.multiplatform.library`, `android {}`
  DENTRO de `kotlin {}`, `withHostTest {}`, dos targets iOS con framework estático `LumeMedLink`).
  Todo el producto vivirá aquí, en el árbol de ADR-0008: hoy sólo existe `app/App.kt` — un
  placeholder sin estilo, porque no hay design system todavía (S0.3 espera el veredicto del autor) y
  un placeholder con estilo sería el primer literal prohibido del repo.
- **`:androidApp`** — el shell corrible, app Android plana (la de aplicación no convive con KMP
  desde AGP 9). `allowBackup=false` desde el nacimiento (§8.5); `dataExtractionRules` y FLAG_SECURE
  llegan con S1.2, que es su slice.

Wrapper copiado del gemelo (Gradle 9.7.1 por `distributionSha256Sum`), catálogo con todos los pins
resueltos del handoff, y **lockfiles activos** (`dependencyLocking` en todos los proyectos): el
catálogo pinnea lo que elegimos, el lockfile lo que de verdad resolvió, transitivas incluidas.

## DoD verificado

- `./gradlew clean build` **verde** (1m30s) — incluye `:androidApp:assembleRelease` y los
  frameworks iOS linkeados, con los locks en enforcement.
- `./gradlew :composeApp:compileKotlinIosSimulatorArm64` **verde**.
- CI escrito (`.github/workflows/ci.yml`, macos-15, ambos targets en un job). **Honestidad: jamás ha
  corrido** — el repo no tiene push y el push es del autor. Hasta el primer push, "CI verde" es una
  frase sin referente.

## La mina que el handoff NO traía (y el gemelo sí había pagado)

El handoff listó siete minas de AGP 9; faltó la octava, que mordió al primer build: **con dos
módulos hermanos aplicando el plugin Kotlin, cada uno lo carga en un classloader distinto y el
`KotlinNativeBundleBuildService` compartido revienta la creación de tareas**
(`Could not create task ':composeApp:linkReleaseFrameworkIosArm64'`). El fix es el root
`build.gradle.kts` con los cinco plugins `apply false` — que el gemelo ya tenía, con su comentario,
y el handoff no mencionó porque su autor lo daba por obvio. Se heredó también su segundo fix
preventivo: -Werror apagado SÓLO en las compilaciones de metadata (el KLIB loader reporta módulos
duplicados durante la transición de CMP 1.11 a las publicaciones KMP de androidx; no es nuestro
warning y no es de las compilaciones reales).

## Deuda que este slice deja declarada

- **Deprecations de Gradle 10** en el build (`--warning-mode all` las lista): vienen de los
  plugins (AGP/KMP), no de nuestros scripts. Se re-evalúan al subir AGP — que tiene techo del IDE
  (9.2.1, catálogo).
- El shell iOS (proyecto Xcode que embeba el framework) no existe: no era DoD de S0.1. Llega cuando
  un slice necesite correr en simulador/device iOS.
- `local.properties` apunta al SDK de esta máquina; CI no lo necesita (el runner trae SDK).

## Nota de coordinación

Mensaje del autor durante el slice: «el mismo problema de las transiciones de las animaciones sucede
en el example para Android» — eso es de **LumeUIComposer** y esta sesión no tiene ese contexto (vive
en la sesión del gemelo). Queda registrado para el vault al cierre; no se tocó ese repo (protocolo de
sesiones concurrentes).
