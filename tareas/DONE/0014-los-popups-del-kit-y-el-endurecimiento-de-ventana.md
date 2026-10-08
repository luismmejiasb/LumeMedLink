# 0014 · Los popups del kit, ¿quedan bajo el endurecimiento de ventana?

> **Estado:** DONE (2026-10-08)
> arreglar nada.
>
> **Bloqueo:** necesita `0013` (el kit cableado) y el emulador.

## De dónde sale

La auditoría de encaje (`../LumeUIComposer/docs/audits/lumemedlink-fit-2026-10-06.md`, «Suspicion»). Lo que se sabe
leyendo código, sin haberlo corrido:

- `MainActivity.kt` endurece la ventana de la actividad: `filterTouchesWhenObscured` (tapjacking, ADR-0010) y
  `denyAutofillExport()` (ADR-0024), los dos sobre `window.decorView`.
- `LumeAlert`, `LumeMenu` y `LumePickerField` dibujan con el `Popup` de Compose (`LumeAlert.kt:114`, `LumeMenu.kt:218`,
  `LumePickerField.kt:282,297` en el kit), y en Android un `Popup` es **otra ventana**.
- FLAG_SECURE probablemente llega (`PopupProperties` por defecto hereda la política de su padre). El filtro de toques y
  la exclusión de autofill están puestos en una vista de la que el popup no cuelga.

Si se confirma: una confirmación de «Cancelar cita» se puede tapjackear, y un campo puesto dentro de un popup se le
entrega al servicio de autofill.

## Cómo se mide (el método de este repo: control en vivo)

1. **Tapjacking.** Un test instrumentado con una ventana superpuesta encima de un `LumeAlert` abierto: ¿llega el toque
   al botón? Control: el mismo test sobre la pantalla principal, que debe rechazarlo.
2. **Autofill.** Un volcado de la estructura de autofill con un campo dentro de un `LumeAlert`. Control: el mismo campo
   en la pantalla principal, que no debe aparecer.
3. **FLAG_SECURE.** `screencap` con un popup abierto: negro, como el resto (bitácora 0009 tiene el método).

## Si se confirma

El arreglo no puede vivir sólo en la app: la app no alcanza la ventana de un popup. La forma probable es un gancho del
kit que se aplique a la vista raíz de cada popup que dibuja (una función de endurecimiento que la app provee por
`CompositionLocal`). Eso es una adición al contrato del kit: **se pide en el kit** (`../LumeUIComposer/tareas/`), con
esta medición adjunta, y se dice en el gemelo LumeUIKit aunque iOS no lo necesite (§0.4 del kit).

## Qué NO hacer

- No dar el hueco por cierto sin la medición, ni por inexistente sin ella.
- No esquivar los popups del kit con diálogos propios: sería un componente en una pantalla.

## Medido — 2026-10-08; espera al kit

`Scripts/verify-popup-hardening.sh`, en el emulador API 37, con todos sus controles válidos:

| Medición | Resultado |
| --- | --- |
| A · FLAG_SECURE: `screencap` con la alerta abierta (control: el launcher, luma 83) | **negro** (luma 0.1): el popup hereda FLAG_SECURE |
| B0 · ventana principal, sin superposición: tocar «Mostrar contraseña» | el toque llega (control) |
| B1 · ventana principal, a través de una superposición de OTRA app | **rechazado**: `filterTouchesWhenObscured` funciona |
| B2 · alerta, sin superposición: tocar «Cerrar» | la cierra (control) |
| B3 · alerta, a través de la superposición | **la cierra igual: el popup se puede tapjackear** |
| Autofill | no aplica: ningún popup del kit tiene un campo de texto (leído en sus fuentes) |

**Confirmada la mitad del toque, descartadas las otras dos.** Dos golpes en el instrumento, atrapados por controles: los
argumentos con espacios se parten en `adb shell` (se pasa una clave), y el primer blanco del control de la ventana
principal —el enlace «¿Olvidaste tu contraseña?»— no servía, porque la pantalla que abre se titula con las mismas
palabras: el texto «seguía ahí» aunque el toque hubiera navegado. Sin el control B0, B1 habría pasado por una
protección que nadie midió.

El arreglo es del kit (la app no alcanza la ventana del popup): pedido el 2026-10-08 — que sus popups pongan
`filterTouchesWhenObscured` en su vista raíz. Se cierra cuando B3 salga verde con los controles verdes.

## Cerrada — 2026-10-08

El kit arregló lo medido (su commit `ae2f97d`): todo popup que dibuja —`LumeAlert`, los dos de `LumeMenu`, las dos
presentaciones de `LumePickerField`— pone `filterTouchesWhenObscured` en la vista raíz de su ventana, siempre, sin
opción. Re-medido con `Scripts/verify-popup-hardening.sh`: **B3 verde** —un toque a través de la superposición de otra
app ya no cierra la alerta— con los cuatro controles verdes y la captura de la alerta todavía negra. Nada que cablear en
la app.
