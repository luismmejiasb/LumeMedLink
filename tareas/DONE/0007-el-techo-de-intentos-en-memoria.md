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

## Cierre — 2026-10-07

**Construido:** el contador vive en el almacén tier 1 como `SecureStoreKey.FAILED_UNLOCK_ATTEMPTS`, detrás de
`FailedAttemptLedger`, y entra solo en el wipe del logout y en `SecureStoreWipeTest`. Se lee **antes** de mostrar el
prompt (un presupuesto gastado en otro proceso termina la sesión sin un intento más), un fallo se escribe **antes** de
devolver el veredicto, y un contador que no se puede leer, escribir o interpretar termina la sesión con una razón propia
(`ATTEMPTS_UNRECORDABLE`) — un valor corrupto nunca se lee como cero. Se resetea en un desbloqueo exitoso y en el logout,
como pedía la tarea. ADR-0034, bitácora 0038.

**Verificado:** ocho tests nuevos en `SessionLockTest`, en los dos targets, incluido el de la tarea (cuatro fallos,
`SessionLock` nuevo sobre el mismo store, uno más → `SessionEnded`). Siete cebos corridos, todos rojos por el test que
lleva su nombre; el de la tarea, volver al `var` en memoria, tumba el test del proceso que muere.

**Lo que se decidió y no estaba en la tarea:** no cobrar el intento antes del prompt. Mientras cancelar sea gratis y un
dedo equivocado no cierre el prompt, quien puede matar el proceso a mitad del prompt puede cancelarlo; el cobro anticipado
no le quita nada. Y algo que la tarea no decía: el techo cuenta **sesiones de prompt fallidas, no dedos**; contra quien
prueba dedos, lo que corta primero es el bloqueo del sistema operativo. Ambas cosas, escritas en ADR-0034.

**Fuera:** el número 5 sigue sin ADR (la tarea pedía no tocarlo); en Android un contador manipulado se lee como «nunca
escrito» por el defecto de la `0003`, que espera su ADR.
