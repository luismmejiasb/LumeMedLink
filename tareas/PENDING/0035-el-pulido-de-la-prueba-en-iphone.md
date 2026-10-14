# 0035 · El pulido de la prueba en iPhone

> **Estado:** PENDING (abierta 2026-10-13) · ADR-0057 · Lo de la app está en curso. Lo del kit está derivado a
> `lumeuicomposer-*` (su `tareas/DONE/0058` y siguientes).

## De dónde sale

El autor probó la app en su iPhone (iOS 27) y trajo once puntos de pulido. Seis son del kit y cinco de la app.

## Qué se hace

### En el kit (derivado por SendMessage el 2026-10-13)

1. `LumeSheet` aparece y se cierra sin animación en el device. Se pide la curva del sheet nativo.
2. El sheet de la foto deja un espacio abajo por el que se ve el fondo.
3. `LumePhoneField` no tiene «continuar» en iOS, porque el teclado telefónico no tiene tecla de retorno. Hace falta
   la barra del teclado.
4. Un toque en un espacio vacío suelta el foco de cualquier campo, en todas las pantallas.
5. Una pila de navegación con push y pop como los de iOS, y el gesto de volver interactivo.
6. `LumeCard(onClick)`. **Hecho** en el kit `76ad95c`, y ya consumido por la tarjeta de «Mis citas».

### En la app

1. **Hecho**: Configuración tiene Apariencia, Idioma, Privacidad (con el switch de telemetría), Términos, Licencias
   generadas, el copyright y la versión al pie, y la confirmación de «Cerrar sesión» (ADR-0057).
2. Los sheets para editar teléfono y correo, verificados con un código por SMS y por correo antes del cambio. Son una
   maqueta, sin pedidos al backend (memoria del autor, 2026-10-11).
3. El ícono de la app en iOS y Android, desde `Handoff/02-Assets/marca/lumemedlink-appicon.svg`.
4. Cuando llegue el punto 5 del kit, migrar `PatientMainHost`, `SearchHost` y `AccessHost` al host del kit y borrar
   sus `AnimatedContent`.

## Qué NO hacer

- No resolver en la app lo que falta en el kit: ni sheets, ni transiciones, ni barras de teclado propias.
- No escribir texto legal que afirme algo que el código no hace.
- No editar `licenses.tsv` a mano: se regenera con `Scripts/generate-licenses.py`.

## Cómo se verifica

- `Scripts/check-licenses.sh` en verde, y sus dos cebos rojos en `rehearse-gates.sh`.
- `SettingsTest`: las rutas, la confirmación, el tema y el idioma persistidos y borrados por el wipe, y las licencias.
- En el device: cambiar el tema y el idioma, y que la app entera cambie sin perder la pestaña.
