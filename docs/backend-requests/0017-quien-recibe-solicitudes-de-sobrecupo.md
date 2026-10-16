# Encargo al backend — qué profesional recibe solicitudes de sobrecupo

> **Cómo usar este documento:** es el prompt completo para el agente de `lumemed-cloud-platform`. Viene de una decisión
> del autor del 2026-10-16, tomada por formulario (ADR-0069 de LumeMedLink), al cablear tu tarea `0157`
> (`ADR-0105`, contrato 0.135.0).

---

## PROMPT

Eres el agente de desarrollo de `lumemed-cloud-platform`. LumeMedLink va a cablear `requestMyOverbook`,
`listMyOverbookRequests` y `withdrawMyOverbookRequest`. Falta una sola cosa para poder ofrecerlo.

### El problema

La paciente sólo puede pedirle sobrecupo a un profesional cuya regla `patientRequests` lo permite, y esa regla viene
**apagada por defecto**. Cualquier otro profesional responde `404`, sin decir por qué, como pide tu `ADR-0105`. Pero
**el directorio no lo dice**: ni `searchDirectory` ni `getDirectoryProfessional` traen la regla. La app tendría que
ofrecer «Pedir sobrecupo» a todos y fallar con casi todos, o no ofrecerlo nunca.

### Lo que necesitamos

1. **Un booleano en el profesional del directorio**, por ubicación o por profesional según cómo guardes la regla:
   «recibe solicitudes de sobrecupo». En `searchDirectory` y en `getDirectoryProfessional`.
2. **Que diga lo mismo que la vista `overbook_requestable_professional`**: si el booleano es `true`, el pedido no
   responde `404` por la regla. Si la paciente no está enrolada en esa clínica, dinos qué responde el campo.
3. El `404` de `requestMyOverbook` se queda como está: la app lo lee como «ya no recibe solicitudes» (la regla pudo
   cambiar entre leer y pedir).

### Lo que no pedimos

- Ninguna hora de sobrecupo en la disponibilidad: la app nunca ofrece un sobrecupo como hora reservable (ADR-0069).
- Los motivos ni la urgencia en el directorio.

Responde con el contrato (el campo, en qué operaciones y su semántica exacta).
