# Encargo al backend — la sede de una cita, y un profesional del directorio por su id

> **Cómo usar este documento:** es el prompt completo para el agente de `lumemed-cloud-platform`. Sale del cableado de
> las citas de LumeMedLink (su tarea `0036`, 2026-10-14), contra el contrato 0.128.0. Sin esto, reagendar no se puede
> cablear.

---

## PROMPT

Eres el agente de desarrollo de `lumemed-cloud-platform`. LumeMedLink está cableando las citas del paciente contra tu
contrato y encontró dos huecos que bloquean flujos que el contrato ya ofrece.

### 1 · El `locationId` de la cita

`rescheduleMyAppointment` exige `locationId` (el id opaco del directorio), y `getDirectoryAvailability` también, para
mostrar los cupos de esa sede. Pero `PatientAppointmentDto` no lo trae: tiene `locationLabel` y `locationAddress`, y no
el id. La app no tiene de dónde sacar la sede de la cita que quiere mover.

**Pedimos `locationId` en `PatientAppointmentDto`**: el mismo id que publica `DirectoryEntryDto.locations[].id`.
Debe ser `null` cuando la hora no es en una sede (visita a domicilio, llamada), o cuando la sede ya no se puede
reservar. Si es `null`, la app no ofrece mover a otra hora de esa sede.

### 2 · Un profesional del directorio por su id

«Agendar de nuevo» (el detalle de una cita pasada) abre el calendario del mismo profesional. La app tiene su
`professionalId` (en la cita y en `listMyProfessionalContacts`), pero no puede leer su ficha de directorio, con sus
sedes, sin buscarlo por nombre. Y buscar por nombre es frágil: un homónimo o un nombre corregido dan otra persona.

**Pedimos `getDirectoryProfessional`** (`GET /v1/directory/professionals/{professionalId}`), con la misma forma que un
ítem de `searchDirectory`. Debe responder 404 cuando el profesional ya no está en el directorio, igual que `null`
en la cita.

### Lo que no pedimos

- Que la cita traiga los cupos, ni nada de la ficha clínica.

Responde con el contrato.
