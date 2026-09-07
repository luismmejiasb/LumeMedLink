# 0025 · El mapa estaba viejo, y por eso el canal de seguridad no habría llegado nunca

**Fecha:** 2026-09-07 · **Origen:** trece días sin sesión. Antes de construir nada, contrastar el
plan contra el contrato real. El backend hizo **39 commits** en ese tiempo.

## Lo que había cambiado sin que nosotros lo supiéramos

| Nuestro plan decía | La realidad |
| --- | --- |
| `ADR-0036` (alcance por app): «aceptado, falta construirlo» | **Construido el 2026-08-27** |
| `ADR-0035` (tier de paciente): «decidido, la superficie no existe» | **Construido el 2026-08-27**, 9 operaciones |
| Contrato: el que teníamos | **0.32.0**, 94 rutas |

Cuatro ítems de la lista (F9, F11, F15, F16) llevaban **once días** marcados «bloqueado: espera que
el backend construya» sobre algo ya construido.

## El defecto que el re-baseline destapó, y es propio

`HttpSecurityEventReporter` —embarcado el 2026-08-25— serializaba nuestro `SecurityEventKind`
directo al cable. El contrato acepta **su propio** enum de ocho valores:

```
integrityViolation · screenCaptureDetected · reauthFailure · reauthLockout
forcedUpdateShown · killSwitchTriggered · suspiciousLoginReset · securityStorageFailure
```

**Ninguno de nuestros seis nombres está ahí.** El nombre del campo (`kind`) lo había adivinado bien;
el vocabulario, no.

Y lo peor no es el 400. Es que **el reporter no lanza, por doctrina**: cada evento se habría
rechazado en silencio y el canal habría parecido cableado y funcionando. Palabra por palabra, el
fallo que ADR-0023 se escribió para evitar tras ver a LumeMed embarcar este canal contra un no-op y
que la plataforma nunca recibiera un evento.

Lo escribí sabiendo que el cuerpo era inventado —está dicho en el commit y en el ADR— y aun así el
riesgo real que corrí era mayor que el que declaré: dije «un esquema equivocado cuesta un 400 y nada
más». Cuesta un 400 **y la ilusión de tener un canal**.

## Tres detecciones que no tienen nombre allá, y por qué no se fuerzan

`when` exhaustivo sin `else`, así que agregar un kind deja de compilar hasta que alguien decida.
Tres traducen; tres no, y cada vecino cercano sería falso:

- **La clave del tier 2 destruida por un enrolamiento nuevo** no es `securityStorageFailure`: no
  hubo ningún fallo, es el control funcionando. Reportarlo así levanta alarma por un teléfono sano.
- **El origen rehusado** no es `integrityViolation`: ese nombre significa binario manipulado, y esto
  es nuestro propio stack frenando.
- **El 2xx con HTML** tampoco.

En un canal donde **el kind ES el mensaje**, una verdad aproximada es peor que el silencio: la
plataforma no puede distinguirla y va a actuar sobre ella. `backend-requests/0006` los pide por
nombre y ofrece explícitamente la opción «no los quiero».

## Lo que leer el contrato confirmó, en vez de citarlo de segunda mano

- **Cero cabeceras de idempotencia** en las 94 rutas → T13 viva. Nuestro no-reintento en POST y la
  postura online-only quedan **confirmados**, no precavidos.
- **Ninguna ruta de reagendar** → T7 viva: sigue siendo cancelar + re-reservar.
- **La agenda del día tenant-scoped no existe.** Sólo citas por paciente. Es la pantalla principal de
  S1.3 y el propio tablero del backend la registra como deuda hacia nosotros.
- **Sigue sin haber proyecto GCP** — ni staging ni producción. Por eso F11 sigue bloqueado, pero por
  una causa distinta y más honesta que la que teníamos escrita.

## Dos hallazgos de frontera

**1. El contrato ya pide la dirección geolocalizada.** `updateMyPatientProfile` acepta
`street, streetNumber, unit, communeCode, regionCode, postalCode, latitude, longitude`. ADR-0027
quedó validado por el contrato **después** de escribirse — y aparece la consecuencia filosa: el
contrato **espera coordenadas del cliente y no le da ninguna forma sancionada de obtenerlas**. Eso es
exactamente el hueco de `backend-requests/0005`.

**2. `listMyAppointments` devuelve `specialtyCode`.** Nuestro §1.0 admite «existencia, fecha, hora,
lugar/modalidad, con quién» y la especialidad **no está** — mientras la propia constitución dice, en
su párrafo honesto, que una cita con un especialista **revela información de salud**. Queda como
decisión abierta del autor, no se resuelve dentro de un slice.

## La lección, que es de proceso y no de código

**Trece días de ausencia bastaron para que cuatro filas del plan fueran falsas y un archivo
embarcado fuera inútil.** Ninguna prueba lo habría dicho: los siete tests del reporter pasaban, y
pasaban contra un doble que **yo** había escrito, aceptando **mi** vocabulario. Un doble del contrato
sólo vale lo que valga el contrato del que se copió.

Lo barato que lo destapó fue leer el `openapi.json` antes de escribir la primera línea.
