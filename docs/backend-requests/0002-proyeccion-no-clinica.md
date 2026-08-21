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
