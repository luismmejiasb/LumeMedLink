# 0022 · El portapapeles de iOS dentro de los campos

> **Estado:** DONE (2026-10-07; sin medir entre dos equipos) · abierta el 2026-10-07 por la pasada de completitud (tarea `0002`, hallazgo F16), que sobrevivió a
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

## Cerrada — 2026-10-07 (el portapapeles del teléfono, sin medir en dispositivo)

El kit agregó `LocalLumeClipboardPolicy` (su tarea 0023): con `LocalExpiring`, todo campo del kit copia en iOS al
portapapeles general con `localOnly` y vencimiento a los 2 minutos; pegar no cambia. La decisión vive en
`core/input/FieldClipboardPolicy`, y la raíz de la app la envuelve una vez. `check-input-surfaces.sh` exige las dos cosas,
con dos cebos (la raíz sin la política; la política rebajada a `System`), rojos.

**Lo que no se midió, dicho:** que el Portapapeles Universal NO lleve lo copiado al Mac es una propiedad de iOS sobre
dos equipos con la misma cuenta de Apple — no se reproduce en el simulador. El kit fijó la escritura (`localOnly` y
vencimiento) con un test contra un portapapeles con nombre; el proceso de test no tiene portapapeles general. Este repo
fija el cableado.
