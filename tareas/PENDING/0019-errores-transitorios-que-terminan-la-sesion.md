# 0019 · Errores transitorios que terminan la sesión

> **Estado:** PENDING · abierta el 2026-10-07 por la pasada de completitud (tarea `0002`, hallazgo F09), que sobrevivió a
> dos refutadores. Evidencia y comandos: bitácora 0043.

## De dónde sale

«No disponible por ahora» termina la sesión en las dos plataformas: en iOS `errSecInteractionNotAllowed` y
`errSecNotAvailable`; en Android `ERROR_HW_UNAVAILABLE` y el chequeo previo. `Unavailable` se define como «sin forma de
volver», y el resultado es un logout con borrado por algo pasajero. Y la misma situación («el dispositivo se bloqueó») es
un cancelar gratis en Android y un final de sesión en iOS. Junto con la pregunta del `ERROR_TIMEOUT` en `PROGRESS.md`.

## Qué se hace

Una ADR que decida estos casos (propuesta: quedar bloqueado, sin contar, sin logout) y un test común que liste cada
situación con su resultado en las dos plataformas.

## Qué NO hacer

- No cambiarlo sin la decisión del autor: es una dirección de falla.
