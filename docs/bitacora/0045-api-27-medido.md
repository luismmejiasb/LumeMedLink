# 0045 — API 27, medido: el prompt que caía, y dos tests que asumían API 28

**Tipo:** `medición` + `fix` · 2026-10-07

## Qué llega

- **Tarea `0023` cerrada:** el tema de la app pasa a AppCompat. Medido antes y después en un emulador API 27.
- El lado API < 30 de `UnlockKeyContractTest` (tarea `0010`), medido por primera vez.
- `Tier1KeyRotationOnDeviceTest` corregido: asumía API 28+ sin decirlo.

## La imagen

La única imagen de emulador de esta máquina era API 37, así que todo lo de API 26–29 estaba escrito como «no medido». El
autor autorizó instalar `system-images;android-27;default;arm64-v8a` (escribe en el SDK, fuera de `~/Documents/Lume`; entra
en la limpieza del 15 de octubre) y un AVD propio, `LumeMedLink_API27`. El asistente de huella de API 27 no tiene el intent
de enrolamiento directo: se entra por Ajustes → Seguridad.

## El prompt que caía

La auditoría lo había inferido leyendo el dex: debajo de API 28, `androidx.biometric` muestra su propio diálogo de AppCompat,
y el tema de la app era el del framework. Una sonda que abría el prompt real al arrancar lo confirmó: la app cayó con
`You need to use a Theme.AppCompat theme`. Con un tema AppCompat, la misma sonda mostró el prompt, el servicio de huellas
autenticó el dedo enrolado para la app y el diálogo se cerró. (La captura de pantalla salió vacía: es `FLAG_SECURE`, y la
evidencia se tomó de la lista de ventanas y del log del servicio de huellas.)

## Dos tests que asumían API 28

`UnlockKeyContractTest` pasó entero, con la biometría fuerte saltada de forma visible. Pero el de rotación de la `0008` falló:
en API 27 el alias viejo es el correcto, porque el parámetro no existe, y el test exigía retirarlo. Y su segundo test habría
pasado vacío: comparaba fechas de un alias que en API 27 nunca existe (`null == null`). La lección es la del golpe 18 en otra
forma: un test escrito en una sola versión de la plataforma afirma cosas de esa versión sin saberlo.
