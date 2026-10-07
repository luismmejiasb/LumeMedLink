# 0010 · Tests cuya premisa venció, o que prueban por encima de la capa

## De dónde sale

Auditoría del 2026-09-20/21, integridad de tests, más una condición que se cumplió sin que nadie re-corriera
nada.

1. **`KeychainSecureStoreTest` (6 tests, `@Ignore` como clase)** —
   `composeApp/src/iosTest/.../core/session/KeychainSecureStoreTest.kt:28`. Su KDoc decía «el día que
   exista el shell de iOS estos tests corren hosted». **El shell existe desde el 2026-08-25.** Los seis
   siguen saltados **y contados en el total**: el reporte dice 130, de los cuales 6 no corren —
   corren 124. Correrlos hosted exige configurar el
   binario de test de Kotlin/Native dentro de un app host, que el proyecto no hace hoy.
2. **`InstallBoundaryTest.theAndroidSentinelReportsEstablishedOnPurpose`** (commonTest, ~línea 132)
   usa `platformInstallSentinel()`, que en el target iOS **no** es el sentinel de Android: el mismo
   test prueba cosas distintas según dónde corre, y no puede distinguir la rama que le da nombre.
3. **«Cancelar no cuesta un intento»** se prueba en `SessionLockTest` con un doble del gate, por encima
   de la capa donde puede romperse (el mapeo de errores de `BiometricPrompt`/`LAContext` a `Cancelled`).
4. **`UnlockKeyContractTest`** (androidDeviceTest) no puede pasar en API 26–29, y su cuarta aserción se
   salta sola.

## Qué se hace

Uno por uno, y **cada uno con su cebo**: el objetivo no es subir el conteo, es que cada test pueda
ponerse rojo por la razón que su nombre dice.

## Qué NO hacer

- No borrar `@Ignore` para «subir» el conteo sin un host que alcance el keychain: se pondrían rojos por
  `-25291 errSecNotAvailable`, que es el entorno, no el código.

## Cierre — 2026-10-07

**1. `KeychainSecureStoreTest` hosted → separado en la tarea `0015`.** Esta misma tarea lo llamaba «una tajada y no una
edición»; queda con su propio archivo para que no se pierda dentro de un cierre.

**2. El test del sentinel de Android, ahora en `androidHostTest`** (`AndroidInstallSentinelTest`) y afirmando una sola rama:
`ALREADY_ESTABLISHED` y la sesión intacta. **El defecto, reproducido:** con el sentinel de Android purgando en cada
arranque (`hasRunBefore() = false`), el test nuevo se pone rojo y **el viejo de `commonTest` seguía verde** — su `when`
aceptaba la purga como éxito.

**3. «Cancelar no cuesta un intento», probado donde se decide.** El mapeo de errores salió a dos funciones `internal`:
`unlockOutcomeForPromptError` (Android, por código de `BiometricPrompt`) y `unlockOutcomeForKeychainStatus` (iOS, por
`OSStatus`), sin cambiar ningún resultado. `PromptErrorMappingTest` (5) fija que sólo los tres descartes son gratis, que el
bloqueo del sistema y lo no clasificado cuentan, y que sin biometría es `Unavailable`; `KeychainStatusMappingTest` (5) lo
mismo en iOS. Cebos: un descarte contado como fallo (Android e iOS) y el bloqueo hecho gratis, todos rojos por el test con
su nombre. De paso desapareció `PromptResult.Invalidated`, que no producía nadie.

**4. `UnlockKeyContractTest` en API 26–29:** la aserción de «autenticación por uso» espera `0` desde API 30 y `-1` debajo
(cómo se construye la clave en cada lado), y la de «sólo biometría fuerte» se **salta de forma visible** con `assumeTrue`
en vez de `return` — que el reporte contaba como aprobada. **Sólo medido en API 37**: el valor `-1` sale de la documentación,
no hay imagen anterior en esta máquina. Y su KDoc decía que **no necesitaba huella enrolada**, lo contrario de lo que su
primera corrida demostró (bitácora 0010): corregido.

**Una pregunta que salió de fijar el mapeo, para el autor** (en `PROGRESS.md`, «Decisiones abiertas»): `ERROR_TIMEOUT` —
un prompt que expira sin que nadie lo toque— cuenta hoy como intento fallido.
