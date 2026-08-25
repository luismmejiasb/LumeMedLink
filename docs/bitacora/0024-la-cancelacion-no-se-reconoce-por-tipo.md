# 0024 · La cancelación no se reconoce por tipo — se le pregunta al job

**Fecha:** 2026-08-25 · **Origen:** «Movida 3» — construir mi mitad del contrato. La implementación
HTTP del canal de eventos de seguridad (F23) trajo de regalo un defecto en el stack ya embarcado.

## El defecto

Cuando el scope del **llamador** muere a mitad de un request, Ktor entrega el fallo del motor
**envuelto**. La rama `catch (cancellation: CancellationException)` nunca lo ve, el catch amplio de
abajo lo mapea, y el llamador recibe un `AppError` por un evento que no es un fallo.

Dos consecuencias reales: un llamador con reintentos **re-envía** un request que el usuario acaba de
cancelar, y sigue trabajando dentro de un scope que se está destruyendo (§6).

## Es la segunda vez, y es la misma forma

| | |
| --- | --- |
| **F12 / ADR-0016** | `HttpRequestTimeoutException` **ES** una `CancellationException`, así que re-lanzar la cancelación intacta dejaba salir el mensaje de Ktor con la URL y su query. |
| **Ahora** | La cancelación llega envuelta y la rama por tipo no la ve. |

La lección no es «agregar otro tipo a la lista». Las dos veces el error es el mismo: **una
comprobación por tipo responde la pregunta equivocada.** La que discrimina no es *qué excepción es
ésta* sino *¿sigue vivo mi job?* — `currentCoroutineContext().ensureActive()`.

Ahora es gate (`check-cancellation-guard.sh`), no costumbre.

## Y me equivoqué al diagnosticarlo, dos veces seguidas

Puse `ensureActive()` **en el stack** primero. Corrí el test: verde. Quité el guarda: rojo.
Conclusión: «el guarda del stack es sostenedor». **Falso.**

El test nuevo había quedado pegado dentro de **otra clase** del mismo archivo, así que el filtro
`--tests` no coincidía con nada — y el fallo de Gradle por «no se encontraron tests» lo leí como el
cebo funcionando. Dos lecturas, las dos por la razón equivocada.

Se destapó sola cuando corrió la suite completa y el test por fin se ejecutó: **falla**. El guarda en
el stack no dispara, porque ese catch vive en un hook de Ktor que **no corre en el job del
llamador** — no tiene nada cancelado que observar.

Octavo verde-por-razón-equivocada del repo, y **el primero producido por el arnés de verificación en
vez de por el código verificado**. Dos hábitos baratos salen de ahí: mirar **la cuenta de tests**, no
sólo el estado del build; y poner el test en la clase donde uno cree que lo puso.

## Y una tercera vez, en el gate mismo

El primer borrador del gate usaba `[^)]*` para llegar al tipo del catch. Todo catch amplio que
importa en este repo lleva `@Suppress("TooGenericExceptionCaught")` — que **contiene un paréntesis**.
La clase negada se cortaba ahí y nunca llegaba al tipo: el gate era ciego exactamente a la población
para la que fue escrito. Su propio cebo lo destapó. Tercera vez esta sesión que un cebo caza un
agujero **en el gate**, no en el código.

## Lo que quedó, y lo que no

- `HttpSecurityEventReporter`: la mitad cliente de F23, **construida y probada** — nunca lanza, la
  cancelación no se traga, sólo cruza el kind opaco, es POST sin reintento (T13), y viaja **por** el
  stack endurecido y no alrededor. 7 tests.
- El **doble del contrato**: un servidor guionado detrás del stack **real**, no un mock del reporter.
  Es la forma contra la que se construirá el resto de la mitad cliente.
- El stack: **caracterizado, no arreglado**, con test que fija el comportamiento y nombre que
  recuerda que la primera versión de la aserción decía `Retryable` y el valor medido era
  `Unexpected`. Un comentario habría embarcado el valor equivocado; el test no pudo.
- **Lo que NO se hizo, a propósito:** el nombre del campo del cuerpo es inventado. La plataforma
  posee el esquema y nunca se lo dio a esta app. `backend-requests/0004` lo pide, junto con el de
  `/v1/app-availability`. Un esquema equivocado cuesta un 400 y nada más — el reporter no lanza — y
  se cambia en una línea, en un solo lugar.
