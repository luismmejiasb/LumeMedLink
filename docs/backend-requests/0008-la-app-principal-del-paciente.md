# Encargo al backend — lo que le falta al contrato para la app principal del paciente

> **Cómo usar este documento:** es el prompt completo para el agente de `lumemed-cloud-platform`. Viene de la
> decisión del autor del 2026-10-11: LumeMedLink construye la app principal del paciente con la forma del paquete
> de diseño contra un guion local, y lo que el contrato no publica se pide aquí (ADR-0046 de LumeMedLink). Leído
> contra el contrato 0.96.0.

---

## PROMPT

Eres el agente de desarrollo de `lumemed-cloud-platform`. LumeMedLink ya dibuja las cinco pestañas del paciente
(Inicio, Buscar, Mis citas, Mis médicos, Configuración) contra un guion. Casi todo lo que muestran existe en tu
contrato; esto es lo que falta, con la pantalla que lo pide. **Nada de esto es contenido clínico** (ADR-0001 de
LumeMedLink): ni motivo de consulta, ni diagnóstico, ni documento.

### 1 · Un perfil del titular, no uno por clínica

`GET /v1/patient/profiles` devuelve un perfil **por clínica** y ninguno trae el RUT. Configuración muestra
nombre, RUT (no editable), teléfono, correo y «Cuenta verificada» **una vez**. ¿Cuál es la fuente de verdad del
titular y cómo se lee? Si la respuesta es «el perfil vive en cada clínica», dinos cuál mostrar y cómo se edita
el teléfono o el correo (¿en todas?, ¿la corrección la aprueba cada clínica, como dice el diseño?).

### 2 · Dónde es la cita

`PatientAppointmentDto` trae `clinicName` y un `locationLabel`. Inicio dibuja dos líneas: la dirección
(«Av. Providencia 1208, oficina 503») y el centro con la comuna («Centro Médico Lyon · Providencia, Santiago»), y
ofrece «Cómo llegar». Pedimos la dirección estructurada del lugar de atención (calle, número, unidad, comuna).
Coordenadas sólo si las decides tú: «Cómo llegar» puede abrir el mapa con la dirección. Recuerda la regla de
LumeMedLink: una coordenada no es excusa para un mapa de pacientes.

### 3 · Quién atendió, para unir citas y contactos

`PatientAppointmentDto` no trae `professionalId` ni la especialidad del profesional (trae `specialtyCode`, que sí
sirve). Mis médicos muestra «6 atenciones» por médico, y desde una cita se reserva otra vez con el mismo médico o
se le valora. Pedimos `professionalId` en la cita (el mismo id opaco del directorio y de
`professional-contacts`), y `specialtyCode` en `SharedProfessionalContact`.

Y una precisión: ¿`/v1/patient/professional-contacts` lista **a todos** los profesionales que atendieron al
paciente, con `phone`/`email` nulos cuando no comparten, o sólo a los que comparten? El diseño muestra también a
los que no comparten («No comparte su contacto. Agenda desde la app.»).

### 4 · Valoraciones

No existen. El diseño: tras una cita atendida, Inicio pregunta «¿Cómo fue tu atención?» con 1–5 estrellas y
opciones por puntaje (f5a–f5d); Configuración muestra «Mis valoraciones · 3 enviadas». El supuesto pendiente del
paquete es que **son públicas en el perfil del médico**. Pedimos: las citas atendidas por valorar, enviar una
valoración (idempotente, con `clientId` como las reservas) y listar las propias. Ojo con lo que el texto libre
puede contener: un comentario es contenido del paciente y puede traer información clínica — necesita tu
política antes que un endpoint.

### 5 · Avisos

Inicio tiene una campana con un punto de «hay novedades». No existe un buzón del paciente. ¿Habrá uno (la
confirmación de una reserva, un cambio del médico)? Si no, quitamos la campana. Cualquier aviso respeta la regla
de LumeMedLink: push sin contenido, el contenido se busca autenticado.

### 6 · Recordatorios y la ventana para cambiar

Configuración muestra «Recordatorios · 24 h y 2 h antes» y Mis citas «Puedes cambiar o cancelar hasta 24 h
antes». La ventana existe del lado del profesional (`booking-policy`); pedimos que el paciente pueda leerla por
cita o por profesional. Los recordatorios: ¿los envía la plataforma y el paciente elige cuándo
(`notification-opt-outs` sólo apaga canales)?

### 7 · Búsquedas recientes

Buscar muestra las dos últimas búsquedas (especialidad + comuna). Guardarlas en el teléfono es dato personal en
disco; preferimos no hacerlo. ¿Las guardas tú, o las quitamos?

### 8 · Lo que ya está y usamos tal cual

Directorio (`/v1/directory/professionals/search` + `availability` + `closed-periods`), reservar con `clientId`,
cancelar, retirar, reagendar como operación atómica, el vocabulario abierto de `status` (`REQUESTED` se dibuja
«Por confirmar»), y los catálogos de especialidades y comunas. Una pregunta de borde: reservar exige estar
enrolado en esa clínica, y el diseño deja buscar y reservar con cualquier médico de la plataforma. Es la misma
decisión abierta del modelo de acceso del paciente (ADR-0044 de LumeMedLink).

## Respuesta del backend y decisiones del autor (2026-10-11)

El backend respondió punto por punto en su tarea `0121` (`../lumemed-cloud-platform/tareas/PENDING/0121-lo-que-le-falta-al-contrato-para-la-app-del-paciente.md`).
El contrato sigue en 0.96.0.

Lo que decidió el autor sobre esa respuesta:

- **No se le hacen más pedidos al backend desde este repo por ahora**: está ocupado con LumeMed. Todo el lado paciente
  sigue siendo maqueta fiel al diseño contra el guion (ADR-0046), y lo que choque con la plataforma queda anotado aquí
  para cuando el backend tenga tiempo.
- **§8, modelo de acceso**: el guion sigue como lo dibuja el diseño (ADR-0044). Que reservar exija vínculo con la
  consulta (ADR-0035 del backend) queda para cuando se conecte de verdad.
- **§6, recordatorios**: Configuración sigue el diseño al pie de la letra con el guion. Queda anotado que la plataforma
  no manda recordatorios ni deja elegir horarios.
- **§7, búsquedas recientes**: se quitan. No se guardan ni en el teléfono ni en el servidor, como propuso el backend.
- **Aditivos propuestos por el backend**: el autor aprueba los cuatro (`professionalId` en la cita, la dirección del
  lugar en la cita, `cancelCutoffHours` en la cita y `specialtyCode` en el contacto compartido). Se le comunican al
  backend cuando vuelva a tomar pedidos de este repo.

## Lo que decidió el autor en el backend (2026-10-11, commit 8da435e de lumemed-cloud-platform)

- Aditivos aprobados: `professionalId`, la dirección del lugar con la unidad nueva, `specialtyCode` en los contactos y
  `cancelCutoffHours` en la tarjeta.
- El RUT propio va en el perfil por clínica, sin vista de titular.
- La lista de contactos incluye a todo profesional que atendió, con `phone`/`email` nulos si no comparte.
- Valoraciones públicas: sólo el promedio, con estrellas y motivos cerrados, sin texto libre. La maqueta de f5 sigue el
  diseño (con texto) por decisión del autor en este repo, y se ajusta cuando llegue el contrato.
- Habrá buzón del paciente (el push sólo dice «hay novedades»), así que la campana se queda.
- Recordatorios fijos a 24 h y 2 h antes, sólo con el consentimiento dado; el paciente no elige los tiempos.
- Búsquedas recientes en ninguna parte (ya quitadas).
- El pedido `0005` (geocodificación) se descartó por ahora: esta app no tiene autocompletado de direcciones construido.

## Lo construido por el backend (2026-10-12, contrato 0.102.0 y 0.102.1)

Nota `0050` del backend (`../lumemed-cloud-platform/docs/backend-responses/0050-what-the-patient-app-screens-draw.md`).

- **La tarjeta de cita** trae `professionalId`, `locationAddress` (calle, número, `unit`, comuna y región; `null` salvo una
  hora `IN_PERSON` en un lugar) y `cancelCutoffHours` (`0` es hasta el inicio; `null` cancela como `0` y no deja mover).
- **El perfil** trae `rut`, de sólo lectura y por clínica.
- **Mis médicos** lista a todo profesional que atendió, con `specialtyCode` y `phone`/`email` nulos si no comparte. Es un
  cambio de sentido: «está en la lista» ya no significa «comparte algo». La maqueta ya mira los dos canales.
- **Recordatorios** a 24 h y 2 h: trabajo de operador, sin cambio de contrato, sólo con el consentimiento
  `appointment-reminders` vigente. La línea de Configuración puede quedarse.
- Siguen pendientes las valoraciones públicas (sólo el promedio) y el buzón.

Choque con la maqueta (ADR-0046 desviación 16): el plazo del contrato va en horas y el diseño dibuja «hasta 30 min antes»
para un médico. El guion lo guarda en minutos; al conectar, 30 minutos no tiene cómo decirse.
