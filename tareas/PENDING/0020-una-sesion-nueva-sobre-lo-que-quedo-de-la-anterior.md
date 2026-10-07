# 0020 · Una sesión nueva sobre lo que quedó de la anterior

> **Estado:** PENDING · abierta el 2026-10-07 por la pasada de completitud (tarea `0002`, hallazgo F12), que sobrevivió a
> dos refutadores. Evidencia y comandos: bitácora 0043.

## De dónde sale

`SessionManager.establish()` escribe sin vaciar el almacén, y un refresh rechazado sólo borra los tokens (lo decidido por
el autor el 2026-09-26, todavía no aterrizado en el repo) sin avisarle al shell. Hoy no hay caché, así que no filtra
nada; el día que exista (ADR-0022) bajo claves fijas, la cuenta B podría leer la caché de la cuenta A en el mismo
teléfono.

## Qué se hace

En la tajada del login y en la de la primera caché: claves por cuenta, o `establish()` sobre un almacén vacío; y una
señal de «la sesión murió» que el shell lleve por `performLogout`, como hace LumeMed.

## Qué NO hacer

- No borrar todo en un refresh rechazado: el autor decidió lo contrario.
