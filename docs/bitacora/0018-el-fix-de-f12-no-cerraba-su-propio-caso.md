# 0018 · El fix de F12 no cerraba su propio caso principal

**Fecha:** 2026-08-21 · **Origen:** la investigación adversarial de F13 revisó, de paso, el fix que
F12 había embarcado una hora antes. Encontró cinco defectos **en esa corrección**.

## El peor: la fuga que el ADR-0016 declaró cerrada seguía abierta

ADR-0016 dice, con estas palabras, que las excepciones de transporte ya no llevan la URL. Su caso
citado textualmente era el mensaje de timeout de Ktor:
`Request timeout has expired [url=https://…/patients/11111111-1?rut=…]`.

**Ese caso exacto seguía abierto.** `HttpRequestTimeoutException` **es** una `CancellationException`,
y mi guard re-lanzaba la cancelación intacta —correctamente, porque tragarse una cancelación rompe
la concurrencia estructurada (§6)—. Así que el timeout salía con la URL y el query dentro.

**Y mi test lo tapó.** Usaba un `MockEngine` que lanzaba `IOException`, que el guard **sí** atrapa.
El test pasaba, la propiedad no se cumplía. Al reescribirlo de forma fiel —dejando que un
`HttpTimeout` real disparara— falló de inmediato.

Sexta vez en este proyecto que un verde resulta ser por la razón equivocada. Esta vez lo cacé yo,
dudando de un test que pasaba mientras sus cuatro hermanos fallaban.

El arreglo es de una línea y de **orden**: atrapar `HttpRequestTimeoutException` **antes** de la
rama de cancelación.

## Los otros cuatro

2. **Rompí el retry mientras cerraba la fuga.** El guard mapeaba `IOException` a
   `AppErrorException` **dentro** del bucle de reintentos, así que
   `retryOnExceptionIf { cause is IOException }` dejó de coincidir y el reintento acotado por fallo
   de transporte quedó **muerto**. Discriminador: `Retryable(status = null)` es transporte;
   `Retryable(status = 500)` es HTTP.
3. **Un rechazo de origen era invisible.** No se loggeaba, no emitía nada, y salía como
   `IllegalStateException` fuera de la taxonomía — un llamador no podía distinguir «el stack lo
   bloqueó» de un error de programación. Ahora es `AppError.Blocked`, con su línea de log **sin el
   host**.
4. **El redactor dejaba pasar el slug del tenant.** `SAFE_SEGMENT = ^[a-z][a-z-]*$` describía
   palabras de ruta, y describe igual de bien a `clinica-alemana`. **Una forma no distingue un
   sustantivo de un nombre; sólo una lista.** Ahora es una allowlist explícita de las palabras que
   esta app realmente usa: agregar una ruta cuesta agregar sus palabras, y ese costo es el punto.
5. **Un portal cautivo filtraba la URL.** Un 200 con `text/html` —wifi de hotel, de clínica, una
   página de error de proxy— hace que `body<T>()` lance `NoTransformationFoundException` desde la
   etapa de recepción, **fuera de todos los nets** que el stack instala, porque esa excepción se
   levanta en el sitio de llamada. Arreglado donde sí se puede: un 2xx con HTML **no es un éxito**,
   es una interceptación, y ahora se rechaza en la validación antes de que nadie pida el cuerpo.

## Lo que esto enseña sobre el método

Los cinco pasaron revisión y se embarcaron. Los cinco tienen ahora un test que **falla contra el
código anterior** — los escribí primero y los vi fallar los cinco antes de tocar nada.

La lección no es «revisar mejor». Es que **un fix de seguridad necesita su propio adversario**, y
que el test de un fix hay que escribirlo preguntándose *cómo ocurre esto de verdad*, no *cómo lo
provoco fácil*. Lanzar la excepción desde el engine era fácil. Dejar que el timeout disparara era
fiel. Sólo la segunda forma medía algo.

## Corregido también

ADR-0016 afirmaba que ningún mensaje del engine escapa con una URL. Era falso mientras se escribía.
Queda corregido en el ADR, y el KDoc del guard —que aún decía que sólo se loggea la respuesta final
de un reintento— también.
