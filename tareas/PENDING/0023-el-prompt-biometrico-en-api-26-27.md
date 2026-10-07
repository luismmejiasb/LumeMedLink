# 0023 · El prompt biométrico en API 26–27

> **Estado:** PENDING · abierta el 2026-10-07 por la pasada de completitud (tarea `0002`, hallazgo F18), que sobrevivió a
> dos refutadores. Evidencia y comandos: bitácora 0043.

## De dónde sale

El tema de la actividad no es AppCompat, y debajo de API 28 `androidx.biometric` 1.1.0 muestra un diálogo de AppCompat
que lanza `IllegalStateException` con un tema que no lo es (visto en el dex embarcado; no ejecutado). Falla cerrado, pero
el médico queda en un bucle en la pantalla de bloqueo. `libs.versions.toml` afirma lo contrario.

## Qué se hace

Medir en una imagen API 26 o 27 (instalarla escribe fuera de `~/Documents/Lume`: el autor tiene que autorizarlo), y
según el resultado cambiar el tema a uno AppCompat o decidir el minSdk.

## Qué NO hacer

- No dar el arreglo por bueno sin la medición: este repo ya pagó un arreglo de algo que no se disparaba (ADR-0026).
