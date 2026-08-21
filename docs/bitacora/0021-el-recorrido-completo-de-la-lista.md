# 0021 · El recorrido completo de la lista de fortificación

**Fecha:** 2026-08-21 · **Encargo del autor:** «termina todos los F que sigan pendientes
ininterrumpidamente».

## Lo que se puede terminar, y lo que no

Los 23 slices no se pueden «terminar» todos, y decir lo contrario sería el defecto que este programa
lleva veinte bitácoras persiguiendo. Lo que sí se puede: **hacer entera la parte construible de cada
uno y declarar con precisión qué falta y por qué**. Eso se hizo.

Y algo que vale más que el conteo: **los bloqueos resultaron ser sólo dos causas**, no doce.

## Los cerrados en este recorrido

- **F14 · la frontera de datos.** Era `[manual]` porque ningún lint entiende semántica. Sigue sin
  entenderla — pero **puede ver un nombre**, y la frontera la cruzan cosas con nombre. Gate en dos
  idiomas que **declara su propio límite** (no caza un valor clínico en un campo llamado `notes`).
  Y la otra mitad **no es podar en el cliente**: es el pedido 0002, que pide la proyección como
  **recurso propio**. Podar del lado nuestro habría dejado a los bytes clínicos cruzar la red igual.
- **F19 · documentos.** De «sin share sheet» a **ninguna vía de entregar un archivo**: impresión en
  ambas plataformas, creación de documentos, pickers, MediaStore, chooser.
- **F22 · un solo log.** La fachada acepta un **conjunto cerrado de eventos**, jamás un `String`:
  un nombre de paciente no tiene dónde ir. Y el default **no escribe nada, como decisión** — lo que
  llega a logcat sale del dispositivo dentro de un `adb bugreport`, en release, y sobrevive al
  reboot.
- **F8 · la caché.** Decidida **antes de que exista**, que es cuando es barato: todo byte en reposo
  pasa por el `SecureStore`, que ya cifra, ya está excluido del backup y ya lo borra el logout
  verificado en hardware. Una segunda ruta tendría que re-ganar las tres — y **ya vimos fallar
  exactamente eso** con la caché de NSURLSession.
- **F18 · contenido no confiable**, cerrado con lo de F12 más la regla P4.
- **F21 · postura de release** (parte construible): no había bloque `buildTypes` en absoluto.

## Los parciales, con la línea exacta de dónde para

- **F10**: verifiqué lo que sí se puede del lado cliente — **un refresh token gastado nunca se
  vuelve a presentar**, y el par rotado reemplaza al anterior en disco. El `≤15 min` es doctrina del
  backend sin política desplegada: **no lo puedo verificar contra nada**, y lo digo en vez de
  testearlo contra un mock y llamarlo verde.
- **F23**: la costura existe con la regla hecha tipo. El stand-in se llama `NoOpSecurityEventReporter`
  **a propósito** — LumeMed embarcó ese canal cableado a un no-op y la plataforma **nunca recibió un
  evento** mientras su tabla estaba verificada como escribible. Un no-op con nombre neutro se lee
  como funcionando.

## Los bloqueados, y por qué son dos causas y no doce

**El host iOS que no existe** (F7, F17, y las colas de F1 y F3). **El backend construyendo lo que ya
decidió** (F9, F11, F15, F16). Para el segundo grupo, lo accionable ya está hecho: **tres pedidos de
contrato escritos**, y el 0003 desbloquea además nuestro S1.3 porque lleva las tres trampas juntas
(T11 la agenda como ruta que el interceptor no evalúa, T7 reagendar atómico o no hay reagendar, T13
idempotencia o el stack sigue sin reintentar POST).

## La postura que fijé sin esperar al backend

Dos, escritas en el pedido 0003 para que no queden implícitas:

- **La pantalla no compondrá cancelar+reservar.** O existe operación atómica, o no hay reagendar.
- **El stack jamás reintenta un POST** mientras no exista llave de idempotencia. Si llega, se
  revisa; hasta entonces la regla se queda.

Ambas respuestas del backend nos sirven. Lo que no sirve es que quede implícito.
