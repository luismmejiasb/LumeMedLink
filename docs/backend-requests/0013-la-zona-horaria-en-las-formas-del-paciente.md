# Encargo al backend — la zona horaria de la clínica en las formas del paciente

> **Cómo usar este documento:** es el prompt completo para el agente de `lumemed-cloud-platform`. Viene de una
> decisión del autor del 2026-10-14 (ADR-0063 de LumeMedLink). Mientras no exista, LumeMedLink asume
> `America/Santiago` en una sola constante.

---

## PROMPT

Eres el agente de desarrollo de `lumemed-cloud-platform`. Las formas del paciente dan cada hora como un instante en
UTC (`PatientAppointmentDto.startsAt`, `endsAt`; `AvailabilitySlotDto`), y LumeMedLink tiene que mostrarlas en la hora
de pared de la clínica, nunca en la del teléfono, que es la regla de LumeMed. Hoy la zona sólo viaja en las formas de
la consulta (`AgendaDto.timeZone`, `PracticeLocationDto.timeZone`, desde 0.99.0), no en las del paciente.

### Lo que necesitamos

1. **`timeZone` (IANA, como ya lo publicas)** en:
   - `PatientAppointmentDto`: la zona del lugar de esa hora;
   - cada ubicación de `DirectoryEntryDto.locations`: la zona de esa sede, para mostrar los cupos;
   - y, si te resulta más limpio, en `AvailabilitySlotDto` y `ClosedPeriodDto`, para que cada uno se lea solo.
2. **La misma fuente** que usa `AgendaDto.timeZone`, para que médico y paciente vean la misma hora.
3. **En las fechas sin hora** (`ClosedPeriodDto`, feriados), confírmanos que ya son fechas de pared de la clínica y
   no instantes.

### Lo que no pedimos

- Que mandes la hora local además del instante: con la zona, la app la calcula.

Responde con el contrato.
