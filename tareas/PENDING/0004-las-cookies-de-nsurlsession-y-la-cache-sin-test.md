# 0004 · La postura de NSURLSession: la caché sin custodia, las credenciales, y una cookie que no era

## De dónde sale

La auditoría del 2026-09-20/21 y su corrección del 2026-09-24.

1. **La caché sin custodia.** `applyLumeCachePosture()` en
   `composeApp/src/iosMain/.../core/networking/PlatformHttpEngine.ios.kt` es el control que F12 ganó con
   esfuerzo —la caché de URL escribía **el bearer token a disco**—, y **no tiene ni test ni gate**
   (`grep -rl applyLumeCachePosture composeApp/src/*Test Scripts` → nada), aunque su KDoc lo presenta
   como un seam al que un test y un gate pueden apuntar.
2. **`URLCredentialStorage` sigue siendo el compartido.** Nuestro bloque no lo toca, y Ktor tampoco
   (la tabla de strings de su klib 3.5.2 no lo nombra). Hoy no se usa autenticación HTTP que lo llene,
   pero es un almacén persistente del sistema al que nada le dijo que no.
3. **Las cookies: la premisa original era FALSA, y se corrige aquí.** La auditoría afirmó que
   `HTTPCookieStorage` quedaba en el compartido y que un `Set-Cookie` sobreviviría al logout — una
   «asimetría no declarada» con Android. **No es así, o casi seguro no**: la tabla de strings del klib
   `ktor-client-darwin-iosSimulatorArm64Main-3.5.2` muestra que `createSession` llama, en este orden,
   `defaultSessionConfiguration`, `setupProxy`, **`setHTTPCookieStorage`** y recién después nuestro
   `configureSession { applyLumeCachePosture() }`. Ktor maneja las cookies con su propio plugin y anula
   las del sistema antes de darnos la configuración. **Lo que no está medido** es el argumento (se lee
   `setHTTPCookieStorage` en el klib, no `null`), y que un upgrade de Ktor lo conserve.

## Qué se hace

- En `applyLumeCachePosture()`: `setURLCredentialStorage(null)`, y **también** `setHTTPCookieStorage(null)`
  y `setHTTPShouldSetCookies(false)` explícitos — no porque hoy falten, sino porque hoy dependen de un
  detalle interno de Ktor, y en esta familia un default no es una decisión.
- Un test en `iosTest` que construya la configuración **pasando por el engine real** y afirme los
  campos — así un upgrade de Ktor que cambie el orden o el valor se ve.
- Un gate que afirme las **asignaciones** (ADR-0029: la llamada, no la palabra), con cebo en
  `rehearse-gates.sh`.

## Qué NO hacer

- No declarar en el threat model una asimetría de cookies que no existe.
- No afirmar nada de disco sin tráfico real: esta tarea cierra la configuración, no la observación.

## Cómo se verifica

El test, visto rojo quitando cada asignación.
