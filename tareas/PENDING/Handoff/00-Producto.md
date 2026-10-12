# LumeMedLink

App de **pacientes** (y una vista sencilla para el médico) del ecosistema Lume.
iOS y Android, construida en **Kotlin Multiplatform**. Marca pendiente: será un **espejo de
LumeMed en UI/UX**.

## La frontera con LumeMed — regla dura
**LumeMedLink JAMÁS accede a información médica de LumeMed.** Ni historia clínica, ni
diagnósticos, ni recetas, ni exámenes, ni controles, ni instrumentos. Solo maneja datos de
contacto, perfiles públicos y citas.

| | LumeMed | LumeMedLink |
|---|---|---|
| Para quién | Médicos (y su secretaria) | Pacientes (y vista simple para el médico) |
| Plataforma | iOS nativa, orientada a iPad | iOS + Android, Kotlin Multiplatform |
| Datos médicos | Sí, solo rol médico verificado | **Nunca** |
| Agenda | Completa: Mi agenda, horario, sobrecupos, estadísticas | Reservar, cambiar, cancelar; el médico la ve simple |

## Roles

**Médico (LumeMed)** — acceso completo, verificado con su registro de prestador tras el alta.
Puede asignar una **secretaria** que administra sus citas y la información de **contacto** de
sus pacientes, nunca la clínica.

**Paciente (LumeMedLink)**
- Edita su información personal de contacto, o solicita la corrección de un dato.
- Busca médicos de la plataforma por especialidad.
- Agenda, cambia y cancela citas.
- Ve el registro de sus citas y el contacto de los médicos que lo atendieron, **si el médico lo
  permite**.

**Médico (LumeMedLink)**
- Actualiza su información y foto de perfil.
- Ve su agenda de forma sencilla.
- Ve a todos los pacientes que ha atendido como **lista de contactos**, con sus datos de contacto.

## Decisiones de producto (oct 2026)
| Tema | Decisión |
|---|---|
| Formato | Teléfono y tablet |
| Interfaz | **Compose Multiplatform compartida**: idéntica en iOS y Android, sin Material |
| Marca | Teal y marca LumeMed, provisorios hasta definir la propia |
| Acceso del paciente | RUT + contraseña |
| Acceso del médico | Se **vincula desde LumeMed** con un código o QR; no tiene cuenta aparte |
| Secretaria | Solo en LumeMed |
| Corrección de datos | La aprueba el médico o su secretaria |
| Contacto del médico | El médico lo comparte **una vez, para todos sus pacientes** |
| Primera versión | Reserva, mis citas, perfil, valoraciones, reclamos y recordatorios. Sin teleconsulta en la app |

### Reglas de reserva (vienen de Mi agenda en LumeMed)
- Solo se ofrecen horas dentro del **horario de atención** del médico.
- Se respetan la **anticipación mínima** y la **reserva máxima**.
- Hay un **plazo mínimo para cambiar o cancelar**.
- El **sobrecupo nunca se ofrece** al paciente.
- **El médico confirma cada reserva**: el paciente envía una solicitud, no una cita confirmada.

### Supuesto pendiente de confirmar
- Las valoraciones son **públicas** en el perfil del médico.

## Decisiones tomadas
- **Formato:** teléfono y tablet.
- **Interfaz:** Compose Multiplatform, idéntica en iOS y Android; ignora Material.
- **Marca:** escudo de LumeMed sin cambios con acento azul `#3E63DD` (fuerte `#2F4FBE`, claro `#91A8F2`, tinte `rgba(62,99,221,.12)`). Nombre: «LumeMed» en tinta + «Link» en azul. Hoja: `Design System/Marca/LumeMedLink Marca.dc.html` · SVG en `Design System/Marca/LumeMedLink/`.
- **Acceso del paciente:** RUT + contraseña.
- **Acceso del médico:** se vincula desde LumeMed con un código o QR.
- **Secretaria:** solo usa LumeMed.
- **Corrección de datos:** la aprueba el médico o su secretaria.
- **Contacto del médico:** se permite una vez, para todos sus pacientes.
- **Reservas respetan:** horario de Mi agenda, anticipación mínima y reserva máxima, plazo para
  cancelar o cambiar, el sobrecupo no se ofrece, y el médico confirma cada reserva.
- **Primera versión incluye:** valoraciones, reclamos y recordatorios. Teleconsulta queda fuera.
- **Se empieza por:** paciente · buscar médico + reservar cita.

## Reglas heredadas
- Usa LumeUIKit (`Design System/`) completo; **solo cambia el acento** de teal a azul.
- El **RUT** sigue siendo el identificador único.
- Lo que el contrato del backend no publique se declara ausente, no se simula.
