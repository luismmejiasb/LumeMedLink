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
