# 0005 · El primer run real de CI: falló por el Xcode del runner y costó ~176 minutos

**Fecha:** 2026-08-21 · **Contexto:** el autor pusheó los 15 commits; el workflow (aún con trigger
`push`) corrió en `macos-15`: **17m36s → BUILD FAILED**, y a facturación 10× de macOS son ~176 de
los 2.000 minutos del plan. La cuenta llegó al 90% ese mismo ciclo (entre todos los repos) y el
autor ordenó pausar Actions.

## El fallo, diagnosticado

`:composeApp:linkDebugTestIosSimulatorArm64` — el LINKER, no nuestro código:

```
ld: Could not find or use auto-linked framework 'UIUtilities': not found
Undefined symbols: _OBJC_CLASS_$_UIViewLayoutRegion  ← in compose ui-uikit-cache
```

**Causa raíz:** el runner `macos-15` selecciona Xcode 16.4 (SDK iOS 18.5), y Compose
Multiplatform 1.11.1 referencia símbolos del **SDK de iOS 26** (`UIViewLayoutRegion`, framework
`UIUtilities`). En local nunca se vio: esta máquina linkea con **Xcode 26.6** (verificado hoy,
`xcodebuild -version`). Todo lo demás del run pasó — gates, lint, Android, compilaciones iOS; murió
exactamente al linkear el binario de tests del simulador, 15 minutos adentro.

Del mismo log, benignos y ya conocidos: los avisos KLIB duplicados de metadata (carve-out de
-Werror, bitácora 0002) y el warning del prebuilt de skiko (18.5 vs deployment 15.0).

## Lo hecho

1. **CI pausado** (commit `c060101`): trigger sólo `workflow_dispatch` — correr CI vuelve a ser un
   acto deliberado. Gates separados a job **ubuntu (1×)**; el build macOS sólo arranca si pasan.
2. **Fix del Xcode** (este commit): `runs-on: macos-26` + step «Select Xcode 26» que elige el
   Xcode 26 más nuevo de la imagen y **falla en segundos con error claro** si no existe — la
   alternativa era volver a quemar 15 minutos hacia el mismo símbolo indefinido.

## Lo que queda SIN verificar, dicho en voz alta

El fix del runner **no se ha corrido**: verificarlo cuesta minutos que hoy no hay (quedan ~139 del
ciclo ≈ 13 reales de macOS). Se verifica en el primer `workflow_dispatch` que el autor dispare tras
el reset del ciclo. Hasta ese día, «CI verde en GitHub» sigue siendo una frase sin referente — lo
que sí está verde, y verificado hoy en esta máquina, es la batería local completa.

## La lección de costos (para toda la familia)

macOS factura **10×**: el plan de 2.000 minutos son **200 reales** de runner macOS — un solo push
de familia iOS se lo come. Reglas que este repo adopta y los hermanos deberían mirar: jamás
trigger `push` con job macOS en cuenta personal; gates y lint a ubuntu; y la salida estructural es
un **runner self-hosted en la máquina del autor** (0 minutos facturados, el Xcode correcto ya
instalado) — decisión del autor, propuesta en sesión.
