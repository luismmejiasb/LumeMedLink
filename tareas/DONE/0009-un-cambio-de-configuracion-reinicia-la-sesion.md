# 0009 · Un cambio de configuración en Android reinicia la sesión de UI

## De dónde sale

Auditoría del 2026-09-20/21, fragilidad (sobrevivió 2 de 3). `MainActivity` no declara `configChanges`
(`androidApp/src/main/AndroidManifest.xml:25`) y el estado de la sesión (`hasSession`, `locked`,
`SessionLock`) vive en `remember` dentro de `App()`, no en algo que sobreviva a la recreación de la
Activity.

## Por qué importa

Rotar, cambiar el tema o el tamaño de fuente recrea la Activity, y con ella la composición: la sesión
se vuelve a sondear y el lock vuelve a nacer **bloqueado** — fail-closed, así que no es un agujero,
pero es una re-autenticación biométrica por girar el teléfono. Y como el contador de intentos también
vivía ahí (tarea 0007), **rotar también lo reseteaba**. *(Actualizado 2026-10-07: ya no — el contador vive en el
almacén tier 1 desde ADR-0034. Lo que queda de esta tarea es el lock que nace bloqueado y la sesión que se vuelve a
sondear.)*

## Qué se hace

Esto es arquitectura (§5: MVVM con `StateFlow` en un ViewModel), y la pieza que falta es justamente el
ViewModel del shell. **Va con el slice de login (S1.1)**, no antes.

## Qué NO hacer

- No arreglarlo con `android:configChanges` en el manifiesto: tapa el síntoma y deja el estado de
  seguridad colgado de una composición.
- No persistir `locked=false` para sobrevivir a la rotación: eso sí sería fail-open.

## Cerrada — 2026-10-07

`ShellViewModel` (`app/`) guarda la ventana de inactividad y la respuesta del sondeo; el lock se reconstruye en cada
composición alrededor de esa ventana, porque el gate biométrico sostiene la Activity y un ViewModel que lo guardara la
filtraría. Sólo en memoria: un proceso muerto sigue naciendo bloqueado. Sin `configChanges`. ADR-0039 (una dependencia,
`lifecycle-viewmodel-compose`, mismo grupo y versión que `lifecycle-runtime-compose`).

**Cómo se verificó:** compila en los dos targets con todos los tests; gate en `check-biometric-contract.sh` (un
`InactivityLock(` fuera del ViewModel es rojo) con su cebo. **No medido de punta a punta**: sin login no hay sesión que
rotar — llega con S1.1, como F4.
