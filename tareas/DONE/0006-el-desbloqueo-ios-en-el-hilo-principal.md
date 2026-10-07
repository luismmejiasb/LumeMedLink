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

## Medición del 2026-10-07 — inconcluyente por construcción, y por qué

Se midió en el simulador (iPhone 17, iOS 27.0) con una sonda temporal en un worktree descartable: enrolar el ítem del
tier 2 y llamar `unlock()` desde la composición —el hilo principal—, con un latido `NSLog` en Main cada 250 ms y capturas
de pantalla.

- `unlock()` volvió **en milisegundos con `Unlocked`**, sin ningún prompt en pantalla y sin enviar coincidencia de Face ID;
  el latido siguió.
- **Control 1:** sólo desbloquear (sin re-enrolar) tras reenviar la notificación de enrolamiento: `Unlocked`.
- **Control 2, el decisivo:** con Face ID **des-enrolado**: `Unlocked` igual. En un dispositivo eso es imposible para un
  ítem `.biometryCurrentSet`.

**Conclusión: el simulador no aplica la ACL biométrica de un ítem del Keychain.** No hay prompt, así que no hay nada que
bloquee el hilo, y esta tarea no se puede medir en ningún simulador. Tampoco ninguna otra propiedad del tier 2 de iOS
(ADR-0011, enmendada el mismo día). Bitácora 0041.

**Lo que sí hay, leído y no medido:** la documentación de Apple de `SecItemCopyMatching` (leída el 2026-10-07) dice
*«SecItemCopyMatching blocks the calling thread, so it can cause your app's UI to hang if called from the main thread.
Instead, call SecItemCopyMatching from a background dispatch queue or async function.»* No es el razonamiento de la
auditoría: es el contrato de la plataforma.

## Lo que espera, y de quién

O se mide en un iPhone físico (build firmado con el equipo del autor, Face ID enrolado), o el autor decide aplicar el
arreglo con la documentación como evidencia: correr la lectura en un dispatcher de IO **inyectado**, `unlock()` sigue
siendo `suspend`. La pregunta está en `PROGRESS.md`, «Decisiones abiertas». Lo de «no arreglarlo antes de medirlo» se
mantiene hasta que el autor diga otra cosa.

## Cierre — 2026-10-07

**Decisión del autor:** aplicar el arreglo con la documentación de Apple como evidencia, porque ningún simulador puede
medirlo. **Construido:** `KeychainUnlockGate` recibe un `ioDispatcher` inyectado (IO por defecto) y la lectura que muestra
Face ID corre en él; `unlock()` sigue siendo `suspend`. `check-biometric-contract.sh` lo exige, con cebo. **Sin medir en
un iPhone**: queda como confirmación pendiente, y ya no la contaminaría la falta de `NSFaceIDUsageDescription` (agregada
en la `0002`).
