# 0022 · El portapapeles de iOS dentro de los campos

> **Estado:** PENDING · decidida por el autor el 2026-10-07; bloqueada en el kit · abierta el 2026-10-07 por la pasada de completitud (tarea `0002`, hallazgo F16), que sobrevivió a
> dos refutadores. Evidencia y comandos: bitácora 0043.

## De dónde sale

La enmienda de ADR-0013 (2026-10-06) abrió copiar dentro de campos y declaró el costo en Android. En iOS lo copiado va al
portapapeles general sin `.localOnly` ni vencimiento (visto en el binario): el Portapapeles Universal lo lleva a los otros
equipos de la misma cuenta de Apple.

## Qué se hace

Declararlo en ADR-0013, y que el autor decida si se marca (pide API nueva del kit y cuesta pegar entre equipos).

## Qué NO hacer

- No marcarlo desde una pantalla: es del kit o de `core/`.

## Decidida — 2026-10-07; espera al kit

El autor eligió la opción B: lo copiado dentro de un campo en iOS va al portapapeles **sólo local** y **vence a los 2
minutos** (ADR-0042). Cómo copia un campo lo decide el kit; pedido el mismo día. Al llegar, se cablea en
`core/input/SensitiveTextField` y se mide en el simulador: copiar, esperar el vencimiento, el portapapeles vacío.
