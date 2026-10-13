# Encargo al backend — reportes de crash de LumeMedLink, sin datos personales

> **Estado: RETIRADO, jamás enviado (2026-10-13).** El autor derogó la prohibición de Firebase (ADR-0052 de
> LumeMedLink) y Crashlytics reemplaza a este reporte; el backend no tiene nada que construir. Se conserva como
> registro.
>
> **Antes:** escrito y NO enviado. El autor pausó los pedidos al backend desde este repo (2026-10-11); éste espera a
> que los reabra. Nace de ADR-0051 de LumeMedLink (2026-10-12), que eligió reportes propios en vez de Crashlytics.

---

## PROMPT

LumeMedLink necesita saber cuándo se cae, sin mandar la causa a un tercero. Su constitución prohíbe los SDK de crash
y analytics (§8.1), y el autor eligió reportes propios a la plataforma.

### 1 · Lo que se pide

Un endpoint **sin autenticación** (como `GET /v1/app-availability`) que reciba un reporte con forma cerrada:

- `exceptionType`: el tipo de la excepción.
- `frames`: hasta ocho marcos del código de la app (`className`, `functionName`, `line`).
- `appVersion`, `appBuild`, `platform` (`ANDROID` / `IOS`), `osVersion`.
- `occurredOn`: el día.
- `crashKey`: **opcional**. Un número al azar sólo para crashes, presente únicamente si la persona consintió.

### 2 · Lo que el cuerpo NO trae, y conviene que el servidor rechace

- El mensaje de la excepción, ni ningún texto libre.
- `Authorization` ni `X-Lume-Installation`: el reporte no se une a una cuenta ni a un teléfono.

Un campo desconocido debería responder `400`, para que un cliente futuro no pueda ensanchar el canal sin un cambio
de contrato.

### 3 · Lo que conviene que decidas tú, y nos digas

- Retención de los reportes, y de `crashKey` en particular. Es un seudónimo: dato personal bajo la 21.719.
- Límite de tasa. El cliente manda a lo sumo tres por apertura, sin reintento, porque la ruta no tiene clave de
  idempotencia (T13).
- Cómo se ven después: una agregación por `exceptionType` y por marco superior, para el equipo, sin exportar el
  `crashKey`.

### 4 · Formato del output

El `operationId`, la ruta, la versión de contrato y la respuesta a §3.
