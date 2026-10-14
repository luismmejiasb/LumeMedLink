# 0035 · El pulido de la prueba en iPhone

> **Estado:** PENDING (abierta 2026-10-13) · ADR-0057 · Todo construido; **falta verlo en el device**. Lo del kit está derivado a
> `lumeuicomposer-*` (su `tareas/DONE/0058` y siguientes).

## De dónde sale

El autor probó la app en su iPhone (iOS 27) y trajo once puntos de pulido. Seis son del kit y cinco de la app.

## Qué se hace

### En el kit (derivado por SendMessage el 2026-10-13)

1. `LumeSheet` aparece y se cierra sin animación en el device. Se pide la curva del sheet nativo. **Hecho** en el
   kit `f3375e5`: la transición llegaba ya terminada al primer cuadro del `Popup`. Ahora es un resorte, y lo mismo
   en `LumeAlert` y en el panel del selector. Falta verlo en el device.
2. El sheet de la foto deja un espacio abajo por el que se ve el fondo. **Hecho** en el kit `f3375e5`: un `Popup` de
   iOS se queda dentro del área segura, y ahora ocupa la pantalla entera. Falta verlo en el device.
3. `LumePhoneField` no tiene «continuar» en iOS, porque el teclado telefónico no tiene tecla de retorno. Hace falta
   la barra del teclado. **Hecho** en el kit `e247cf7`: los teclados de teléfono, número y decimal (y el del código)
   llevan el Done del sistema, que avanza en un `LumeForm` o termina. Nada que cambiar en la app; falta verlo en el device.
4. Un toque en un espacio vacío suelta el foco de cualquier campo, en todas las pantallas.
   **Hecho** en el kit `b8184f8` (`lumeReleasesFocusOnTap()`), y aplicado una vez en la raíz de `App()`.
5. Una pila de navegación con push y pop como los de iOS, y el gesto de volver interactivo. **Hecho** en el kit `227a717`
   (`LumeNavigationStack`).
6. `LumeCard(onClick)`. **Hecho** en el kit `76ad95c`, y ya consumido por la tarjeta de «Mis citas».

### En la app

1. **Hecho**: Configuración tiene Apariencia, Idioma, Privacidad (con el switch de telemetría), Términos, Licencias
   generadas, el copyright y la versión al pie, y la confirmación de «Cerrar sesión» (ADR-0057).
2. **Hecho** (`76e27a3`): los sheets para editar teléfono y correo. El perfil cambia sólo después de confirmar el
   código enviado al contacto **nuevo**. Son una maqueta, sin pedidos al backend (memoria del autor, 2026-10-11): el
   contrato vive en `PatientCareGateway` y lo cumple el guion. Los contadores de reenvío subieron a
   `core/screen/Countdown.kt`, porque ahora los usan dos áreas.
3. **Hecho**: el ícono de la app desde `Handoff/02-Assets/marca/lumemedlink-appicon.svg`. En iOS (`ca53951`) es un PNG
   de 1024 sin canal alfa. En Android (`cf6fd63`) es adaptativo y vectorial, con el escudo dentro de la zona segura.
4. **Hecho**: `PatientMainHost` (la raíz de la pestaña, el detalle y el cambio de la cita, y las páginas de
   Configuración), `SearchHost` y `AccessHost` usan `LumeNavigationStack`, y sus `AnimatedContent` se borraron. En el
   ingreso, el arrastre del borde vuelve sólo donde la ruta permite volver.

## Qué NO hacer

- No resolver en la app lo que falta en el kit: ni sheets, ni transiciones, ni barras de teclado propias.
- No escribir texto legal que afirme algo que el código no hace.
- No editar `licenses.tsv` a mano: se regenera con `Scripts/generate-licenses.py`.

## Cómo se verifica

- `Scripts/check-licenses.sh` en verde, y sus dos cebos rojos en `rehearse-gates.sh`.
- `SettingsTest`: las rutas, la confirmación, el tema y el idioma persistidos y borrados por el wipe, y las licencias.
- En el device: cambiar el tema y el idioma, y que la app entera cambie sin perder la pestaña.
