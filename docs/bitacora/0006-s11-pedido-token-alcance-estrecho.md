# 0006 · S1.1 arranca: el pedido del token que no puede abrir la ficha

**Fecha:** 2026-08-21 · **Encargo del autor:** empezar S1.1 por el pedido de contrato.

## Qué se escribió

`docs/backend-requests/0001-token-de-alcance-estrecho.md` — el primer pedido formal de este repo al
backend, en el formato que LumeMed ya validó (su 0001 produjo la ADR-0033 del backend): un prompt
completo para el agente de `lumemed-cloud-platform`, que define QUÉ y restricciones, jamás el CÓMO.

**El argumento central:** hoy un token de médico no distingue desde qué app nació, así que la
frontera de LumeMedLink (ADR-0001: cero contenido clínico) sería una promesa del lado cliente. El
pedido exige que el servidor la aplique — deny-por-defecto con lista explícita de operaciones
(Fase 1: `/v1/me`, login+MFA, perfil no clínico, security-events, app-availability), regla de oro
(ninguna operación con contenido clínico entra jamás; DTO mixto no entra tal cual), fallo cerrado a
la manera de su ADR-0009 (404, jamás 403), y tokens ≤15 min con refresh rotado. El escenario que
cierra: un token exfiltrado del teléfono abre a lo sumo una agenda, jamás una ficha.

**Deslindes explícitos**, para que el pedido no se desborde: NO es el tier paciente (ese es su
ADR-0031 §4 y la fecha la decide el autor), y agenda/contactos NO van aquí — llegan con S1.3/S1.4 y
sus preguntas T11/T16. Sí se anuncian, para que el diseño del alcance nazca extensible por lista.

**Aprovechamiento honesto:** el pedido invita (decisión del backend) a cerrar de paso la deuda ya
registrada en su tablero — `X-Lume-Client-Platform`/`X-Lume-Client-Version` fuera de `parameters`
del contrato — porque esta app suma dos plataformas nuevas al ecosistema.

## Nota de método

La carpeta `docs/adr/` del backend no fue legible desde esta sesión (permisos); el pedido se
escribió desde el tablero (`ECOSYSTEM-STATUS.md`), que es exactamente para lo que existe: las citas
a ADR-0009/0031 del backend salen de sus filas §2.5/§3.1 con su texto citado. Si el agente del
backend encuentra una cita desviada, su constitución manda y lo declara.

## Estado

S1.1 🟡: pedido esperando el ADR del backend. Mientras responde, la mitad sin UI del slice
(`core/session`) puede avanzar. El autor entrega el pedido al agente del backend cuando decida.
