# Encargo al backend — el token de alcance estrecho de LumeMedLink

> **Cómo usar este documento:** es el prompt completo para el agente de `lumemed-cloud-platform`.
> Copialo entero como encargo. LumeMedLink es el consumidor; este texto define QUÉ necesita y bajo
> qué restricciones — el CÓMO es del backend y de su propia constitución, que manda sobre cualquier
> sugerencia de implementación hecha aquí.
>
> Pedido prometido en `ECOSYSTEM-STATUS.md` §3.1 («un token de scope estrecho que pedirá por
> `backend-requests/` antes de su primera lectura autenticada») y exigido por la ADR-0003 de
> LumeMedLink antes de que la app haga su primer request autenticado (su slice S1.1).

---

## PROMPT

Eres el agente de desarrollo de `lumemed-cloud-platform`. Tu misión es diseñar y publicar el
mecanismo por el cual **un médico autenticado desde LumeMedLink recibe un token que NO puede leer
ni escribir contenido clínico**, aplicado por el servidor. Hoy la plataforma alimenta a dos apps
con el mismo contrato; este encargo existe para que las alimente con **dos alcances distintos**.

Trabaja bajo tu propia constitución y tus ADR — donde este encargo y tu constitución choquen,
manda tu constitución y **lo declaras en el output** en vez de resolverlo en silencio.

### 0 · Contexto que ya está decidido (no re-decidir)

- **LumeMedLink** es la segunda app de la familia (tu tablero §3.1): phone-first, Kotlin
  Multiplatform, para la gestión **no clínica** del médico y, a futuro gated, el paciente. Su
  frontera constitucional (ADR-0001 de LumeMedLink) es lista cerrada: perfil, citas sin motivo
  clínico, contactos, señalización futura de teleconsulta. **Cero ficha, cero diagnósticos, cero
  documentos.**
- **El médico es el mismo humano con la misma cuenta** de Google Identity Platform y la misma MFA
  TOTP obligatoria (ADR-0007 de LumeMed; ADR-0003 de LumeMedLink). No se crea identidad nueva.
- **Este encargo NO es el tier de paciente.** Ese requiere su propio ADR tuyo — identidad,
  proofing, consentimiento, threat model («a second authentication tier, a role outside the
  Membership model», tu ADR-0031 §4) — y su fecha la decide el autor (decisión abierta en tu
  tablero §6). Nada de este documento lo prejuzga.
- **Tres numeraciones de ADR independientes** (backend, LumeMed, LumeMedLink): toda referencia en
  tu output dice de quién es el número, nunca el número pelado.
- Todo lo nuevo entra por el **contrato OpenAPI versionado** — lo que no está en `openapi.json`
  publicado no existe para la app.

### 1 · El problema que este encargo resuelve

Hoy un token de médico es un token de médico: el backend no distingue **desde qué app** llega la
sesión, y el RBAC (tu ADR-0009) autoriza por rol y membresía, no por cliente. Consecuencia: si
LumeMedLink usara el flujo tal cual, el teléfono llevaría un token capaz de leer la ficha entera —
y la frontera de la app quedaría como promesa del lado cliente.

**La separación de ambientes que justifica la existencia de LumeMedLink sólo es real si el
servidor la aplica.** El escenario que este encargo cierra: un token exfiltrado del teléfono
(dispositivo compartido, la amenaza priorizada del threat model de LumeMedLink) debe abrir, como
máximo, una agenda — jamás una ficha.

### 2 · Qué se pide

1. **Distinguir la app cliente.** Que el backend sepa, de forma no falsificable por el cliente,
   si una sesión de médico nació en LumeMed o en LumeMedLink. El mecanismo es tuyo (audience o
   client distinto en Identity Platform, custom claim, token exchange que baja privilegios, un
   tier propio — tu ADR decide); la propiedad requerida es que **el alcance viaje en el token o se
   derive de él en el servidor**, nunca de un header que la app declare.
2. **El alcance LumeMedLink, cerrado por construcción.** Deny-por-defecto: una operación entra al
   alcance sólo por lista explícita. La lista de Fase 1 (lado médico) que la app necesita:
   - `GET /v1/me` — identidad y `platformRole`.
   - El flujo de **login + atestación MFA** existente del médico (confirmar en el output que sirve
     tal cual desde una segunda app, o qué cambia).
   - **Perfil del médico**: lectura y edición de sus campos no clínicos (cuáles existen hoy y cuál
     operación los sirve lo defines tú en el output).
   - `POST /v1/security-events` — la app lo consume igual que LumeMed (§8.16 de ambas
     constituciones). Decláralo: ¿mismo set de `kinds` o se registran nuevos para esta app?
   - `GET /v1/app-availability` — pre-auth, pero listado aquí porque esta app suma **plataformas
     nuevas** al ecosistema (Android e iOS de LumeMedLink) y tu tablero ya registra que
     `X-Lume-Client-Platform`/`X-Lume-Client-Version` faltan en `parameters` del contrato. Si al
     diseñar el alcance formalizas la identidad de app/plataforma, es el momento de cerrar también
     esa deuda — decisión tuya, decláralo.
   - **Anunciadas, NO incluidas**: las lecturas de agenda (S1.3) y de contactos/roster (S1.4)
     llegarán por pedidos separados **con sus preguntas T11** (restricción de tratamiento en rutas
     tenant-scoped, `UNENFORCEABLE_PATIENT_ROUTES`) y T16 (vocabulario de borrado). Diseña el
     alcance sabiendo que crecerá por lista, no lo congeles a estas cinco entradas.
3. **La regla de oro del alcance**: ninguna operación que devuelva o acepte contenido clínico
   entra jamás — notas, diagnósticos, resultados, archivos médicos, recetas, chat. Y si una
   operación del alcance sirve un DTO **mixto** (campos clínicos junto a no clínicos), no entra
   tal cual: la app necesita la vista sin lo clínico (minimización, Ley 21.719 — y tu trampa T1:
   esta app no transporta documentos, ADR-0007 de LumeMedLink).
4. **Fallo cerrado y silencioso.** Una llamada con token LumeMedLink a una operación fuera del
   alcance responde como respondes al no-miembro (tu ADR-0009: 404, nunca 403 — o lo que tu
   constitución mande; decláralo). El alcance no se anuncia en el error.
5. **Política de tokens confirmada**: acceso ≤15 min y refresh con rotación (ADR-0003 de
   LumeMedLink). Si ya es así para médicos, confírmalo en el output; si no, decláralo como brecha.

### 3 · Qué devuelve este encargo (output esperado)

1. **Tu ADR** del alcance por app cliente — el mecanismo, sus consecuencias, y por qué.
2. **Los cambios de contrato versionados** (si los hay: operación de perfil, formalización de
   plataforma/app, lo que tu diseño necesite) publicados en `openapi.json`.
3. **La lista viva del alcance LumeMedLink** en el lugar que tu constitución elija, con la regla
   de extensión (cómo un pedido futuro agrega una operación).
4. **Las confirmaciones pedidas**: MFA desde segunda app · kinds de security-events · política de
   tokens · forma del fallo fuera-de-alcance.
5. **Sandbox**: que un token de alcance LumeMedLink funcione contra el corpus sintético — el DoD
   del lado app es cablear login y ver `/v1/me` responder con datos sintéticos usando ese token.

### 4 · Qué NO pide este documento

Agenda, contactos, reagendar (T7), nada del lado paciente, ni push. Cada uno llega con su pedido y
sus preguntas cuando su slice abra. Este encargo es exactamente uno: **que exista el token que no
puede abrir la ficha.**
