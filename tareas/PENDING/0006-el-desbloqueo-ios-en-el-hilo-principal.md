# 0006 · El desbloqueo de iOS corre en el hilo principal

## De dónde sale

Auditoría del 2026-09-20/21, dimensión biometría (**razonado, no medido**; sobrevivió 2 de 3 refutadores).
`KeychainUnlockGate.unlock()`
(`composeApp/src/iosMain/.../core/session/KeychainUnlockGate.kt:117`) llama `SecItemCopyMatching`
sobre un ítem con control de acceso biométrico (línea 131) dentro de una `suspend fun` que **no cambia
de dispatcher**. Esa llamada **bloquea el hilo que la hace** mientras el sistema muestra el prompt de
Face ID / Touch ID; la auditoría razonó que, si ese hilo es Main, el prompt no puede presentarse.
**Puede no ser así**: la hoja biométrica la dibuja el sistema en otro proceso y quizá aparezca igual,
con la app congelada detrás — que sigue siendo un defecto, pero otro.

## Qué se hace

Primero **medirlo** —el hallazgo es razonado—: llamar `unlock()` desde Main en el host iOS y ver si el
prompt aparece. Si se confirma: correr la lectura en un dispatcher de IO **inyectado** (§6: jamás
hardcodeado), y que `unlock()` siga siendo `suspend`.

## Qué NO hacer

- No mover todo `KeychainSecureStore` a otro hilo por las dudas: el tier 1 no lleva prompt y no tiene
  este problema.
- No arreglarlo antes de medirlo. Este repo ya «arregló» una vez algo que el experimento después
  mostró que no se disparaba (ADR-0026).

## Bloqueo relacionado

El fortification plan dice que el lado iOS del tier 2 espera el trabajo; esta tarea es una de sus
precondiciones.
