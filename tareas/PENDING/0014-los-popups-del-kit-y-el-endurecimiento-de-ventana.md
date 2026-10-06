# 0014 · Los popups del kit, ¿quedan bajo el endurecimiento de ventana?

> **Estado:** PENDING · Abierta el 2026-10-06. Es una **sospecha sin medir**, escrita así a propósito: se mide antes de
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
