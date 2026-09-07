# Encargo al backend — tres detecciones de LumeMedLink que tu vocabulario no nombra

> **Cómo usar este documento:** es el prompt completo para el agente de `lumemed-cloud-platform`.
> Es chico y concreto. **El pedido 0004 ya está respondido** — no hizo falta que contestaras: el
> esquema estaba en el contrato 0.32.0 y se leyó de ahí el 2026-09-07. Esto es lo que esa lectura
> destapó.

---

## PROMPT

Eres el agente de desarrollo de `lumemed-cloud-platform`. `POST /v1/security-events` acepta un
`kind` de una lista cerrada de ocho valores. **LumeMedLink detecta tres cosas que no están en esa
lista**, y prefiere no reportarlas antes que reportarlas con un nombre aproximado.

### 0 · Contexto, para que no parezca un capricho

El vocabulario de ocho valores se escribió para LumeMed. LumeMedLink es Kotlin Multiplatform, tiene
su propio stack de red y su propio modelo de amenaza, y detecta cosas que la app clínica no puede
detectar. Tres de sus seis kinds no tienen traducción:

| Lo que LumeMedLink detecta | Por qué el vecino más cercano sería **falso** |
| --- | --- |
| **La clave del tier 2 fue destruida por un enrolamiento biométrico nuevo** | El candidato es `securityStorageFailure`. Pero no hubo ningún fallo: es el control **funcionando como fue diseñado** (`setInvalidatedByBiometricEnrollment`). Reportarlo así levanta una alarma por un teléfono sano. |
| **El stack rehusó enviar a un origen que no es el suyo** | El candidato es `integrityViolation`. Pero ese nombre significa que el binario o el runtime fueron manipulados; esto es **nuestro propio cliente frenando**, y no dice nada del dispositivo. Es además la señal de que alguien intentó redirigir tráfico con el bearer adentro. |
| **Llegó un 2xx con `text/html`** — portal cautivo o algo interceptando | Mismo problema: no es integridad del binario. Es la red del usuario, y es exactamente lo que uno querría ver agregado por red y no por dispositivo. |

Las dos últimas son señales de **red hostil**, y hoy tu canal no tiene forma de recibirlas de ningún
cliente.

### 1 · Lo que se pide

**Decidí una de estas tres, y decilo con esas palabras:**

1. **Agregar valores al enum** (aditivo, no rompe a nadie). Si es así, dános los nombres exactos que
   vas a usar — no los inventamos nosotros.
2. **Decir que no los querés.** Perfecto y se acata: LumeMedLink los deja como evento local y su ADR
   lo registra como decisión tuya, no como omisión suya.
3. **Proponer un mapeo tuyo**, si ves un valor existente que para vos SÍ significa eso. Vos sos el
   dueño de la semántica de tu canal; si `integrityViolation` en tu tablero quiere decir «algo raro
   pasó en el borde del cliente», decilo y lo usamos.

### 2 · Una cosa que conviene que sepas de cómo lo estamos usando

`POST /v1/security-events` responde **429**. LumeMedLink **no reintenta** — ni ahí ni en ningún POST
de este canal — porque la ruta no tiene clave de idempotencia (tu trampa T13) y un reintento
convertiría un evento en tres. Si esperabas que el cliente reintentara con backoff, decilo, porque
hoy no lo hace y es deliberado.

### 3 · Formato del output

Una respuesta corta: la opción elegida, los nombres exactos si agregás valores, y la versión de
contrato donde aterrizan.
