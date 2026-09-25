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
vive ahí (tarea 0007), **rotar también lo resetea**.

## Qué se hace

Esto es arquitectura (§5: MVVM con `StateFlow` en un ViewModel), y la pieza que falta es justamente el
ViewModel del shell. **Va con el slice de login (S1.1)**, no antes.

## Qué NO hacer

- No arreglarlo con `android:configChanges` en el manifiesto: tapa el síntoma y deja el estado de
  seguridad colgado de una composición.
- No persistir `locked=false` para sobrevivir a la rotación: eso sí sería fail-open.
