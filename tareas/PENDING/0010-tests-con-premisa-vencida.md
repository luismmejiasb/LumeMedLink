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
