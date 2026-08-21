# 0016 · F6: el «sin backup» que no cubría la migración entre teléfonos

**Fecha:** 2026-08-21 · **Encargo:** segundo slice de Fase B, trabajado con investigación
adversarial en paralelo (cinco ángulos independientes + síntesis) antes de tocar código.

## El ataque, en lenguaje humano

Cambias de teléfono. El nuevo te ofrece «copiar todo del anterior» y en diez minutos tienes tus
apps con sus datos. Eso es exactamente lo que un atacante quiere: **restaurar tu app en un
dispositivo que él controla**. Lo mismo con un backup de Google en la nube.

Este repo llevaba `allowBackup="false"` desde el primer día y **la documentación lo presentaba como
la defensa**. Sonaba suficiente.

## No lo era, y no lo dedujimos: lo medimos

En un emulador API 37, acotando la medición a nuestro paquete y con control positivo:

| Configuración | Bajo el transporte device-to-device |
| --- | --- |
| **Sólo `allowBackup="false"`** (lo que teníamos) | `progress: 512/1024 … 3072/1024` · **`result: Success`** — **los datos salieron** |
| Añadiendo `dataExtractionRules` con `<device-transfer>` | sin bytes; el transporte no recoge nada |

La causa está en la propia plataforma: a `targetSdk ≥ 31` Android **ignora deliberadamente**
`allowBackup` para la migración entre dispositivos, con el compat change
`IGNORE_ALLOW_BACKUP_IN_D2D` (id 183147249), confirmado en vivo con `dumpsys platform_compat`.
Nosotros apuntamos a 36.

O sea: **era un agujero real y vivo**, y la doc afirmaba lo contrario.

## Lo que la investigación adversarial evitó que hiciera mal

1. **Un consejo que habría abierto una puerta nueva.** Un investigador recomendó agregar una sección
   `<cross-platform-transfer>` «por simetría». Es **opt-in**: escribirla es lo que *habilita* la
   exportación Android↔iOS. Otro investigador lo contradijo y tenía razón. Hoy el gate la prohíbe.
2. **Un falso verde en mi propio gate.** Mi primera versión **contaba etiquetas** `<exclude>` en vez
   de verificar los nombres de dominio. Un cebo lo delató: renombrar `device_file` → `device_files`
   deja nueve etiquetas, el gate pasaba en verde, y **el framework descarta en silencio** un dominio
   que no conoce (sin error de build, sólo una línea de log). Ese directorio seguiría viajando.
   Corregido: ahora se verifican los nueve nombres, y además se rechaza cualquier dominio
   desconocido.
3. **Que `root` no cubre a los demás.** El framework recorre **nueve árboles separados**; excluir
   `root` no excluye `file`, `database` ni `sharedpref`. Los nueve son obligatorios.
4. **Un defecto en mi código de F5.** `KeystoreSecureStore.get()` sólo atrapaba
   `AEADBadTagException`, pero `obtainKey()` y `cipher.init()` están en el mismo bloque y lanzan otra
   familia (`KeyPermanentlyInvalidatedException`, `UserNotAuthenticated…`). Justo los estados que
   produce un dispositivo reseteado: habría convertido una lectura fail-closed en un **crash al
   arrancar**. Ahora atrapa `GeneralSecurityException` e `IOException`.

## Y un bug en mi propio control, que el control encontró

El script de verificación tiene un modo `--with-live-control` que quita la sección, recompila, y
**exige que la fuga reaparezca** — porque «no salieron bytes» sólo es evidencia si el check era
capaz de ver bytes. La primera corrida dijo «el control tampoco vio fuga», y el script se negó a
concluir. Tenía razón: yo medía **después** de restaurar el transporte por defecto, o sea por la
ruta de nube, no por D2D. Corregido, el control extrae 3072 bytes con la sección fuera y cero con
ella.

Es la tercera vez en este proyecto que un control atrapa un resultado que yo habría reportado como
bueno. Ya no es anécdota: **es el método.**

## Lo que NO se verificó, y no se afirma

- **Ninguna migración real entre dos teléfonos.** Toda la evidencia D2D es el transporte de `bmgr`
  más el parseo del framework. El compat change es `@Overridable` y la doc de Google matiza con «en
  dispositivos de algunos fabricantes».
- **API 26–30 es inferencia**: la única imagen en esta máquina es android-37.
- **Que ningún byte llegó al almacén de un transporte** no se observó directamente (`adb root` está
  rechazado en esta imagen). Se observan los veredictos, no el almacén.
- **Herramientas OEM** (Smart Switch, Mi Mover, Clone Phone): sin probar, y plausiblemente fuera del
  camino del BackupManager — en cuyo caso nada de F6 las gobierna.

## Corrección de doc: dos afirmaciones que el código no sostenía

La constitución §8.5 y el threat model nombraban `isExcludedFromBackup` como control vigente de iOS.
**No existe: la app no escribe un solo archivo en iOS**, sólo Keychain — y la clase que usa el build
de device (`WhenPasscodeSetThisDeviceOnly`) es la única que Apple documenta como fuera de todo
backup. Nombrar un control inexistente es exactamente el defecto que este repo persigue. Corregido
en ambos lugares: `isExcludedFromBackup` llega con la primera caché (F8), no antes.

## Hallazgos fuera de alcance, registrados en el plan (no perdidos)

- **Compose exporta la estructura de autofill de cada pantalla, incondicionalmente** — y FLAG_SECURE
  no la toca. Muerde en cuanto S1.4 dibuje el nombre y RUT de un paciente. → F3/F20.
- **La caché NSURLCache de iOS**: el engine Darwin usa la configuración por defecto, así que
  respuestas GET cacheables (la agenda) irían a disco sin cifrar y **sobreviven al logout**, que
  enumera `SecureStoreKey` y no la caché de URL. → F8/F12, y es un defecto de mi stack de red.
- **`adb bugreport`**: un `Log.d` perdido en un slice futuro viaja en un zip que el médico puede
  mandar a cualquiera, en build de release. → F22.
- **Los builds debug son debuggable por defecto** y ningún gate exige `isDebuggable=false` en
  release. → F21.
