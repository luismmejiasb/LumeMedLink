# 0002 · La pasada de completitud de la auditoría nunca corrió

## De dónde sale

La auditoría del 2026-09-20/21 (13 dimensiones, bitácoras 0027–0033) terminaba con **tres críticos de
completitud** —«¿qué archivo no miró nadie?», «¿qué defecto vive en la costura entre dos dimensiones?»
y un pre-mortem— y **los tres murieron contra el límite de sesión**. La auditoría lo declaró así en su
propio informe: **la pasada de «qué no se auditó» no existe, y hay un punto ciego de tamaño
desconocido.**

## Qué se hace

Correr esas tres preguntas contra el árbol **de hoy** (no el de la auditoría: desde `bddc12b` cambiaron ~66 archivos).

1. **Censo**: listar todo lo versionado —incluido lo que no es código: `.gitignore`, config de detekt y
   ktlint, recursos, plists, entitlements, esquemas de Xcode, `Scripts/lib/`, `tareas/`— y contrastarlo
   contra lo que las bitácoras 0027–0033 dicen haber mirado.
2. **Costuras**: defectos que ninguna dimensión sola ve. La forma que ya produjo hallazgos aquí: un
   control correcto cuyo canal de señal no llega a nadie; una propiedad verificada en Android y en iOS
   no; una exención heredada de otro propósito.
3. **Pre-mortem**: asumir una brecha con datos reales y buscar su semilla **en el código de hoy**, con
   `archivo:línea`.

## Límite del autor, no rompible

**Como máximo 5 agentes adversariales por verificación** (decisión del autor, 2026-09-24). La
auditoría original usó 178 según el registro de la sesión que la corrió (no está en ningún archivo del repo); esta pasada tiene que caber en el límite, lo que obliga a elegir bien: un
agente por pregunta, más dos refutadores para lo que sobreviva.

## Qué NO hacer

- No re-auditar lo que ya se arregló con cebo (bitácoras 0027–0033): la pregunta es qué **no** se miró.
- No presentar «no encontré nada» como «no hay nada»: cada agente declara qué miró y qué no.

## Cómo se verifica

Cada hallazgo sobrevive a dos refutadores y trae `archivo:línea` más un comando que lo muestra. Los que
sobrevivan se vuelven tareas nuevas en esta carpeta.

## Cierre — 2026-10-07

**Corrió, dentro del tope del autor: cinco agentes en total.** Tres buscadores —censo, costuras, pre-mortem— sobre el
árbol de hoy, y dos refutadores sobre lo consolidado, uno atacando la evidencia (re-corrió cada cebo sobre una copia propia
de `HEAD`) y otro la consecuencia (alcance, si ya estaba declarado, severidad, si el arreglo propuesto rompía otra cosa).

**Resultado: 21 hallazgos, ninguno refutado** (dieciocho sobreviven, tres parciales), más uno nuevo que encontró un
refutador. Dos los encontraron dos buscadores por separado: la app que volvía del sueño con la sesión abierta (F01) y el
plist sin `NSFaceIDUsageDescription` (F02). Detalle, comandos y veredictos: bitácora 0043.

**Arreglados en el commit que mueve esta tarea**, cada uno con cebo:
- F01 — el shell re-pregunta al lock al volver (ADR-0032 enmendada).
- F02 — `NSFaceIDUsageDescription` en el plist, verificada en el `.app` construido, y exigida por el gate.
- F03, F04, F08, F11, F14, F15, F17, F19, F20 y el del refutador — doce huecos de gates (ADR-0029, tercera pasada): la
  mitad de los parámetros de la clave, el plist y los entitlements parseados, lo que enlaza el host iOS, `setFlags(0, …)`,
  las APIs de iOS desde Kotlin, el sentinel que usa el arranque, el release depurable, los permisos fuera de
  `android.permission.*`, el alcance del gate de cancelación, y el `scan` que saltaba líneas con `/*`.
- F10 — el borrado del tier 2 ya no informa éxito cuando falla (ADR-0014).
- F21 — `.gitignore` ignora `.p8` y `.hprof`.
- La prosa vencida que los refutadores confirmaron (cuatro archivos y dos ADRs).
- El ensayo pasa de 52 a **75** cebos, y un meta-chequeo exige que todo gate de CI tenga al menos uno.

**Declarados y convertidos en tareas** (piden decisión del autor, un dispositivo o una tajada propia): F05 → `0016`,
F06 → `0017`, F07 → `0018`, F09 → `0019`, F12 → `0020`, F13 → `0021`, F16 → `0022`, F18 → `0023`.

**Nombrados y no arreglados, de severidad baja:** `runCatching` alrededor de una llamada suspendida sigue invisible al
gate de cancelación; `kfun.py` toma la primera función de un nombre, así que una sobrecarga declarada antes sirve de
señuelo (reproducido); el launcher sin `taskAffinity=""` en minSdk 26 (sin verificar).

**Lo que la pasada NO hizo:** ningún agente compiló ni corrió nada en un dispositivo; lo de plataforma (relojes en sueño,
Face ID sin la clave, el diálogo en API 26–27, accesibilidad) está leído en binarios o en documentación, no medido. Y los
dos refutadores trabajaron sobre `HEAD` mientras los arreglos se escribían: los arreglos los verifiqué yo, con cebo, no
ellos.
