# 0007 · El techo de intentos fallidos vive en memoria

## De dónde sale

Auditoría del 2026-09-20/21 (memoria/ciclo de vida); nombrado y no arreglado en ADR-0032.
`SessionLock.failedAttempts` es un `private var` (`composeApp/src/commonMain/.../core/session/SessionLock.kt:48`)
dentro de un objeto que el shell crea con `remember`.

## Por qué importa

Matar el proceso —o, según la auditoría, una rotación que recree la composición— **reinicia el
contador**. El techo de 5 intentos que esta app cree imponer no es el que se impone. El bloqueo
biométrico del propio sistema operativo acota el daño (tiene su propio lockout), pero la promesa del
§8.3 es de esta app, no del sistema.

## Qué se hace

Persistir el contador en el `SecureStore` (tier 1, sin prompt) como una `SecureStoreKey` nueva —lo que
además lo mete automáticamente en el wipe del logout, porque `SecureStoreWipeTest` itera el enum—, y
resetearlo sólo en un desbloqueo exitoso o en un logout.

## Qué NO hacer

- No guardarlo en `SharedPreferences`/`NSUserDefaults`. **Ningún gate lo rechazaría**: dentro de
  `core/` P3 y el `ForbiddenImport` de detekt eximen el storage plano, así que la disciplina acá es
  manual (§8.4).
- No subir el techo «para compensar»: el número vive en `SessionLock.kt:4`
  (`DEFAULT_MAX_FAILED_ATTEMPTS`, F4) y **ninguna ADR lo fija** — si se cambia, que sea con una.

## Cómo se verifica

Test: 4 fallos, recrear `SessionLock` sobre el mismo store, 1 fallo más → `SessionEnded`. Con cebo:
volver al `var` en memoria lo pone rojo.
