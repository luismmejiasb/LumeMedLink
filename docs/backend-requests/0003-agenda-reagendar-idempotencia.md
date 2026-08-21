# Encargo al backend — la agenda del día, reagendar atómico e idempotencia

> Prompt completo para el agente de `lumemed-cloud-platform`. Define QUÉ y bajo qué restricciones;
> el CÓMO es suyo y de su constitución, que manda sobre cualquier sugerencia de aquí.
>
> Los tres puntos salen de **sus propias trampas** (T11, T7, T13) y de una deuda que **ustedes
> registraron** en el tablero §3.1: «la agenda del día NO EXISTE como ruta».

---

## PROMPT

Eres el agente de desarrollo de `lumemed-cloud-platform`. Tres cosas, en un solo encargo porque son
la misma pantalla.

Trabaja bajo tu constitución; donde choque con esto, manda la tuya y **lo declaras en el output**.

### 1 · La agenda del día no existe

El contrato sólo tiene citas **por paciente**
(`GET /v1/orgs/{tenantId}/patients/{patientId}/appointments`). LumeMedLink necesita la agenda de un
profesional en un rango — su pantalla principal. Es **construir**, no exponer.

Forma sugerida, el nombre es tuyo:
`GET /v1/orgs/{tenantId}/appointments?professionalId=&from=&to=`

Restricciones que vienen de nuestra frontera (ADR-0001):

- **Sin motivo clínico en ningún campo.** Ni `reason`, ni `notes`, ni `chiefComplaint`, ni un enum
  de motivo. Una cita, para esta app, es: existencia, fecha, hora, lugar/modalidad, con quién.
- El «con quién» sale por la **proyección no clínica** del pedido 0002, no por el `Patient` completo.

### 2 · T11 viaja con esta ruta, y es la razón de que este pedido exista antes que la pantalla

**Es exactamente el escape que ustedes describieron**: una ruta **tenant-scoped sin `{patientId}`**,
de las que el interceptor de restricción de tratamiento (Ley 21.719) **no evalúa**. Un titular con
restricción vigente **reaparece** en la agenda del día salvo que la query lo excluya y la ruta quede
registrada con razón escrita en `UNENFORCEABLE_PATIENT_ROUTES`.

Se decide aquí, en el contrato, no en producción. Lo mismo aplica al roster/contactos (nuestro S1.4).

### 3 · Reagendar: operación atómica o la pantalla no lo ofrece

Su trampa T7: el rol de runtime no puede mover `starts_at`/`ends_at`, así que reagendar hoy es
**cancelar + volver a reservar** — dos escrituras con una ventana en la que el cupo queda libre y
otro lo toma.

**LumeMedLink no va a componer esas dos llamadas.** O existe una operación atómica del servidor, o
la pantalla no ofrece reagendar. Decidan cuál y dígannoslo; ambas respuestas nos sirven, lo que no
sirve es que quede implícito.

### 4 · Idempotencia para toda escritura

Su trampa T13: sin idempotencia, un reintento por mala señal **fabrica una cita duplicada** — y
nuestra postura online-only existe por eso mismo. Pedimos una llave de idempotencia (header, tuyo el
nombre) para agendar y cancelar, con su semántica escrita: cuánto vive, qué devuelve un reintento
con la misma llave.

Nuestro stack **jamás reintenta un POST** (ADR-0004), justamente porque esto no existe. Si llega,
podremos revisar esa regla; hasta entonces se queda.

### 5 · Qué devuelve este encargo

1. La ruta de agenda publicada en el contrato, sin motivo clínico.
2. La respuesta T11 para agenda y roster, con el registro que corresponda.
3. La decisión de T7: operación atómica, o «no hay reagendar» por escrito.
4. La llave de idempotencia y su semántica, o la constancia de que no llega y por qué.
5. Sandbox con datos sintéticos para las dos rutas.
