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
