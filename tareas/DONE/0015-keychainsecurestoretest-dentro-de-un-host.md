# 0015 · Correr `KeychainSecureStoreTest` dentro de un host

> **Estado:** DONE (2026-10-07) · separada de la `0010` el 2026-10-07, que la nombraba como su punto 1 y ya decía que era «una
> tajada y no una edición».

## De dónde sale

`composeApp/src/iosTest/.../core/session/KeychainSecureStoreTest.kt` son seis tests de la mecánica de `SecItem` (alta,
lectura, upsert por borrar-y-agregar, borrado, wipe por servicio) marcados `@Ignore` como clase. La razón, medida en la
bitácora 0007: el runner de tests de Kotlin/Native lanza un proceso **sin app anfitriona** en el simulador, y `securityd`
no le concede ningún keychain (todo responde `-25291 errSecNotAvailable`). Su KDoc prometía correrlos «el día que exista el
shell de iOS»; el shell existe desde el 2026-08-25 y siguen saltados — **contados en el total** (el reporte dice 6
saltados).

## Qué se construye

Un camino para que ese spec corra **dentro de un proceso que tenga keychain** — y en verde por la razón correcta. Opciones a
evaluar, ninguna probada:

1. Que el binario de test de Kotlin/Native corra dentro de un app host (un bundle de XCTest anfitrionado por la app, o el
   soporte que la versión de Kotlin del repo ofrezca para eso). Es la que deja el spec como test de verdad.
2. Un verificador de dispositivo al estilo de `verify-install-sentinel.sh`: inyectar temporalmente en el host un punto de
   entrada que corra el spec y escriba el resultado al contenedor. Más barato, pero el spec deja de ser un test de la suite.

## Qué NO hacer

- No borrar `@Ignore` para «subir» el conteo: sin host, los seis se ponen rojos por `-25291`, que es el entorno y no el
  código.
- No agregar una API pública de test al framework de producción para que el host la llame.

## Cómo se verifica

Los seis corren en verde dentro de un host, y un cebo (por ejemplo, el wipe que deja de filtrar por servicio) pone rojo
al test que corresponde. El total de saltados baja de 6 a 0 **porque corren**, no porque desaparecieron.

## Cerrada — 2026-10-07

Corre **dentro de un simulador arrancado, con entitlements de simulador enlazados en el binario de test** — la opción 1,
sin app anfitriona. Medido con control: `--standalone` (lo que hace Kotlin por defecto) → `-25291`; en un simulador
arrancado sin entitlements → `-34018`; con las dos cosas → verde. `composeApp/build.gradle.kts` enlaza
`src/iosTest/keychain-tests.entitlements` (grupo inventado, nunca el de la app) en `__TEXT,__entitlements` y corre el test
en el simulador que nombra `LUME_IOS_TEST_DEVICE`; sin esa variable la clase se excluye **y la corrida lo dice**. CI
arranca uno y la fija.

**Cómo se verificó:** los seis pasan, más un séptimo nuevo, `wipeLeavesAnotherServiceAlone` — el único que puede ver un
wipe que deja de filtrar por servicio (`wipeClearsTheWholeService` sigue verde con ese defecto). Cebo: el wipe sin
servicio → **ese** test rojo, los demás verdes. Saltados en iOS: 0, porque corren.
