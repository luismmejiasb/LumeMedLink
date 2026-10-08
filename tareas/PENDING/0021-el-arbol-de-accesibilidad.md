# 0021 · El árbol de accesibilidad, un canal sin declarar

> **Estado:** PENDING · decidida por el autor el 2026-10-07; bloqueada en el kit · abierta el 2026-10-07 por la pasada de completitud (tarea `0002`, hallazgo F13), que sobrevivió a
> dos refutadores. Evidencia y comandos: bitácora 0043.

## De dónde sale

Un servicio de accesibilidad habilitado lee el texto de cada nodo aunque haya FLAG_SECURE. Ninguna ADR lo cubre; declarado
en el threat model (T6) el 2026-10-07. La bandera `accessibilityDataSensitive` (API 34+) es parcial: un servicio se
declara herramienta a sí mismo, y la bandera deja fuera a lectores de pantalla legítimos.

## Qué se hace

Decidir la postura (autor), medir con un servicio de prueba en el emulador, y agregar el volcado de accesibilidad y el de
assist al plan de medición de la tarea 0014.

## Qué NO hacer

- No aplicar la bandera sin decisión: corta accesibilidad real.

## Decidida — 2026-10-07; espera al kit

El autor eligió la opción B: los datos sensibles que la persona escribe no se leen; sólo rótulos y textos orientativos
(ADR-0042). **Medido**: la marca puesta como `modifier` del campo del kit queda en la raíz y no llega al nodo editable
(control positivo visto). Pedido al kit el mismo día. `Scripts/verify-accessibility-sensitivity.sh` está en rojo por eso
y se cierra la tarea cuando se ponga verde.
