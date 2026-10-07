# 0008 · La clave tier-1 se reusa por alias sin validar sus parámetros

## De dónde sale

Auditoría del 2026-09-20/21, dimensión cripto (sobrevivió 2 de 3). `KeystoreSecureStore.obtainKey()`
(`composeApp/src/androidMain/.../core/session/KeystoreSecureStore.kt:120`) devuelve la clave del alias
si existe (línea 121) **sin mirar con qué parámetros se creó**. La generación pide
`setUnlockedDeviceRequired(true)` sólo desde API 28.

## Por qué importa

Una clave creada en API 26/27 —sin `unlockedDeviceRequired`— **se reusa para siempre** después de una
actualización del sistema operativo. La degradación que el código acepta en un API viejo se vuelve
permanente en uno nuevo, y nada lo nota.

## Qué se hace

Al obtener la clave, leer su `KeyInfo` y, si no cumple los parámetros que la versión actual exige,
**rotarla**: borrar el alias y regenerar. Rotar la clave invalida lo cifrado con ella, así que
equivale a un logout local — y eso es correcto: es la misma dirección de fallo que un reseteo de
credenciales del dispositivo.

## Qué NO hacer

- No intentar re-cifrar los datos con la clave nueva: no se puede leer con la vieja sin darle a la
  clave vieja un uso más, y el tier 1 contiene tokens que se pueden volver a pedir.

## Cómo se verifica

Test de device con una clave sembrada sin el parámetro. Difícil de reproducir en un emulador de API
alto; si no se puede, **se declara** en vez de fingirlo.

## Cierre — 2026-10-07

**Lo que la tarea proponía no se podía hacer, y eso fue el primer hallazgo:** `KeyInfo` no tiene accesor para
`unlockedDeviceRequired` (revisado contra el `android.jar` de API 36). Leer los parámetros de la clave no ve justo el que
importa.

**Construido:** el alias es la procedencia. En API 28+ la clave vive bajo `lume_session_tier1_udr`, que sólo se crea con
el parámetro; una clave bajo `lume_session_tier1` (la de API 26/27, y la de todo lo hecho antes de hoy) se retira junto
con su texto cifrado —archivos primero, clave después— y se hace una nueva. Es el logout local que la tarea anticipaba. El
wipe borra los dos alias. Enmienda de ADR-0009, bitácora 0040.

**Verificado en el emulador (API 37), con lo que se puede y lo que no:** la actualización del sistema no se reproduce; el
test `Tier1KeyRotationOnDeviceTest` siembra su resultado (alias viejo, sin el parámetro, con un valor en el formato del
almacén) y un control prueba antes que la siembra se lee. Cuatro cebos, todos rojos: sin la retirada, con un solo alias
para toda API (el código de antes), con la clave retirada pero el cifrado conservado, y con el wipe viejo.

**Y un test que se habría vuelto vacío:** los del logout en dispositivo afirmaban que `lume_session_tier1` ya no existe. Con
este cambio ese alias no existe nunca en API 28+, así que la aserción pasaba sin mirar la clave en uso. Ahora afirman todos
los alias, con la precondición de que la clave en uso existía; el cebo del wipe viejo sólo lo atrapan así.
