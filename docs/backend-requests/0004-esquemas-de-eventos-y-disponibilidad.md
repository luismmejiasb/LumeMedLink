# Encargo al backend — los dos esquemas que LumeMedLink consume a ciegas

> **Cómo usar este documento:** es el prompt completo para el agente de `lumemed-cloud-platform`.
> Copialo entero como encargo. Es el más chico de los cuatro y no pide construir nada nuevo: pide
> **publicar el esquema de dos rutas que ya existen**.

---

## PROMPT

Eres el agente de desarrollo de `lumemed-cloud-platform`. LumeMedLink consume dos rutas tuyas cuyo
esquema **nunca le fue dado**, y ya escribió su mitad del cliente contra un cuerpo provisional.

### 1 · `POST /v1/security-events`

La constitución de LumeMedLink (§8.16) la da por existente y LumeMed la consume. LumeMedLink acaba
de implementar `HttpSecurityEventReporter` con este cuerpo, **inventado**:

```json
{ "kind": "SESSION_UNLOCK_FAILED" }
```

Lo que necesita saber:

- **El nombre exacto del campo** y su tipo. ¿`kind`? ¿`type`? ¿`event`?
- **El vocabulario que aceptas.** LumeMedLink tiene seis kinds propios y son **opacos por
  construcción** (`SecurityEventKind`, ADR-0023 de LumeMedLink): sin mensaje, sin usuario, sin id de
  registro — la ausencia de campo libre es el control, no una convención. ¿Los aceptas tal cual,
  esperás un vocabulario tuyo, o hay un prefijo por app?
- **Qué respondes.** ¿202? ¿204? ¿Un 4xx con problem+json si el kind es desconocido?
- **Si distingues la app emisora**, y cómo. Con `ADR-0036` (audiencia separada) el token ya te lo
  dice; se pregunta para no duplicarlo en el cuerpo.

Restricción que no es negociable de este lado: **el canal jamás lleva texto libre**. Si tu esquema
tiene un campo de mensaje, LumeMedLink no lo va a llenar, y conviene que lo sepas antes de diseñar
alertas que lo asuman.

### 2 · `GET /v1/app-availability`

La constitución de LumeMedLink (§8.15) la da por existente, con una decisión ya tomada de su lado:
**falla ABIERTO**. Si la ruta no responde, responde mal, o responde algo que el cliente no entiende,
la app **sigue funcionando** — porque el control autoritativo es que tú rehúses el auth, no que un
cliente se apague solo.

Lo que necesita saber:

- **El esquema de la respuesta**: qué campo dice «bloqueado», qué dice «versión mínima», y sus tipos.
- **Cómo identificas la app y la versión**: ¿header, query, el token?
- Si hay un caso en que esperás que el cliente **se apague duro**. LumeMedLink no lo va a hacer sin
  una razón escrita, porque un kill-switch que falla cerrado es una salida de servicio que se
  dispara sola.

### 3 · Formato del output

Un documento por ruta, con el esquema, un ejemplo de request y de respuesta, los códigos de estado, y
**dónde queda publicado en el `openapi.json`** — LumeMedLink genera su cliente del contrato
(ADR-0004 suyo), así que un esquema que no esté en el contrato no le sirve.

Si alguna de las dos rutas **no existe** como la constitución de LumeMedLink cree, decilo con esas
palabras: es una afirmación falsa en su constitución y hay que corregirla allá, no rellenarla acá.
