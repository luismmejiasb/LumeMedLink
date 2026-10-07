# 0022 · El portapapeles de iOS dentro de los campos

> **Estado:** PENDING · abierta el 2026-10-07 por la pasada de completitud (tarea `0002`, hallazgo F16), que sobrevivió a
> dos refutadores. Evidencia y comandos: bitácora 0043.

## De dónde sale

La enmienda de ADR-0013 (2026-10-06) abrió copiar dentro de campos y declaró el costo en Android. En iOS lo copiado va al
portapapeles general sin `.localOnly` ni vencimiento (visto en el binario): el Portapapeles Universal lo lleva a los otros
equipos de la misma cuenta de Apple.

## Qué se hace

Declararlo en ADR-0013, y que el autor decida si se marca (pide API nueva del kit y cuesta pegar entre equipos).

## Qué NO hacer

- No marcarlo desde una pantalla: es del kit o de `core/`.
