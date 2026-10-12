# Encargo al backend — la proyección no clínica

> **Cómo usar este documento:** es el prompt completo para el agente de `lumemed-cloud-platform`.
> Copialo entero. LumeMedLink es el consumidor; define QUÉ necesita y bajo qué restricciones — el
> CÓMO es del backend y de su constitución, que manda sobre cualquier sugerencia de aquí.
>
> Nace de una advertencia que **ustedes** escribieron (tablero §3.1.1, advertencia nº3), no de una
> sospecha nuestra.

---

## PROMPT

Eres el agente de desarrollo de `lumemed-cloud-platform`. Tu misión es dar a LumeMedLink una forma
de leer los datos **no clínicos** de un paciente **sin que el cuerpo de la respuesta contenga
campos clínicos**.

Trabaja bajo tu propia constitución y tus ADR — donde este encargo y tu constitución choquen, manda
tu constitución y **lo declaras en el output**.

### 0 · Lo que ya está decidido (no re-decidir)

- **`ADR-0036` (tuya, aceptada)** creó el alcance por app cliente: el token de LumeMedLink no
  concede operaciones clínicas.
- **La advertencia nº3 de ustedes mismos**, textual: «El DTO mixto es la trampa real. `Patient`
  lleva `bloodType` cifrado y `careDirective` en la misma fila que correo y teléfono: una lista de
  operaciones permitidas **admite el cuerpo entero** de la respuesta, así que lo no-clínico
  necesita **proyección propia**, no un permiso sobre la entidad.»
- **ADR-0001 de LumeMedLink** es el producto, no un estándar de código: contenido clínico en
  cualquier superficie de esa app es una violación de frontera, no un bug de severidad.

### 1 · El problema, en una frase

Un permiso sobre `Patient` autoriza **la fila entera**. Alcance ≠ proyección: hoy la única forma de
que LumeMedLink lea el teléfono de un paciente es recibir también su grupo sanguíneo.

### 2 · Qué se pide

1. **Una proyección no clínica como recurso propio del contrato** — no un flag, no un parámetro
   opcional sobre el recurso existente. Un parámetro se olvida; un tipo distinto no se puede
   confundir. Nombre y forma tuyos.
2. **Su campo set es una lista cerrada**, y extenderla es un cambio de contrato: nombre, foto,
   teléfono (E.164), correo, previsión. **Dirección: ver §4** — no la incluyas todavía.
3. **Cero campos clínicos en el esquema**, ni siquiera nullables ni «vacíos para este rol». Un campo
   presente-pero-null sigue siendo un campo que un cliente decodifica y un log registra.
4. **El generador debe producir un tipo distinto**, para que un cliente que pide la proyección no
   pueda recibir accidentalmente la entidad completa (tu ADR-0025: el `operationId` es superficie
   de contrato).
5. **Aplica a las dos superficies núcleo** de nuestra Fase 1: el roster/contactos (S1.4) y lo que la
   agenda (S1.3) muestre de un paciente.
6. **La pregunta T11 viaja con esto**: ambas son rutas tenant-scoped, de las que el interceptor de
   restricción de tratamiento no evalúa. Quién excluye al titular restringido y con qué registro
   (`UNENFORCEABLE_PATIENT_ROUTES`) se decide aquí, no en producción.

### 3 · Qué devuelve este encargo

1. La ADR de la proyección — por qué un recurso propio y no un parámetro.
2. El contrato versionado publicado, con el tipo nuevo.
3. La confirmación de que el alcance de `ADR-0036` cubre la operación nueva.
4. La respuesta a T11 para ambas rutas.
5. Sandbox: que la proyección responda con el corpus sintético.

### 4 · Una pregunta abierta que NO decidas por nosotros

**La dirección.** Tu `ADR-0035` deja al paciente editar «foto, correo, teléfono y dirección». La
lista cerrada del §1.0 de LumeMedLink **no incluye dirección**, y extenderla exige un ADR nuestro
que el autor todavía no tomó (registrado en nuestro `PROGRESS.md`). Deja la dirección **fuera** de
la proyección por ahora, o —si te resulta más limpio— inclúyela y **dilo en el output**, para que
la decisión quede visible en vez de heredada.

### 5 · Qué NO pide este documento

Nada clínico, ni siquiera «por si acaso». Ni documentos (ADR-0007 nuestra). Ni el lado paciente,
que tiene su propia fase.

## Respuesta del backend (2026-10-12)

El backend respondió en su nota `0051` (`../lumemed-cloud-platform/docs/backend-responses/0051-the-non-clinical-projection.md`),
contrato `0.103.0` y `0.103.1`, tarea `0117`, `ADR-0089` del backend.

- **El token de LumeMedLink ya no es clínico** (la mitad médica de nuestra ADR-0036). Hasta el 2026-10-12 un médico que
  entraba por esta app recibía un token de clínico completo. Ahora el tenant de IdP de LumeMedLink alcanza sólo `/v1/me`,
  la MFA, los dispositivos y las dos rutas de abajo; el resto responde `404`. En desarrollo, `POST /dev/token` con
  `client: 'LINK_CLINICIAN'`.
- **`listPatientContactCards`**: nombre, teléfono, correo y previsión, como lista cerrada. Sin ningún campo clínico, ni
  siquiera nulo, y sin dirección.
- **`listAgendaCards`**: la fila de la agenda sin lo que es del registro del acto, con los filtros `status` y `channel`.
  Trae el nombre de pila y la inicial del apellido; el nombre completo se cruza por `patientId` con los contactos.
- **T11**: los contactos conservan a la paciente con restricción de tratamiento; la agenda la excluye. Las dos rutas
  tienen su excusa en `UNENFORCEABLE_PATIENT_ROUTES`.
- **Sin sandbox, sin foto, sin dirección.**
- **Abierto para el autor**: un mismo médico es probablemente otro usuario en el tenant de LumeMedLink, y cómo se enlaza
  o se aprovisiona esa cuenta no está decidido (mientras tanto `/v1/me` puede responder `not_provisioned`). Y si el
  clasificador debe cerrar ante un tenant que no reconoce.

Choque con la maqueta (m1, ADR-0046 desviación 15): la tarjeta de agenda no trae si la visita es primera o control; sólo
el canal dice si es teleconsulta.

## Lo que decidió el autor sobre lo abierto (2026-10-12)

- **La cuenta del médico se vincula desde LumeMed.** Con su sesión y su segundo factor en LumeMed, el médico genera un
  código de un solo uso que crea su usuario en el tenant de LumeMedLink, unido al mismo profesional. No se unen cuentas
  por coincidir el correo: un correo compartido o reasignado bastaría para tomar una cuenta de médico.
- **Un tenant que el clasificador no reconoce se rechaza.** Un error de configuración deja a alguien afuera, jamás le da
  un alcance que no debía tener.

Sin comunicar al backend todavía: el autor decide cuándo (ver «No se le hacen más pedidos», 2026-10-11, en el pedido 0008).
