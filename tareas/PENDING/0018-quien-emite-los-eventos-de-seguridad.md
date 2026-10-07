# 0018 · Quién emite los eventos de seguridad del bloqueo

> **Estado:** PENDING · abierta el 2026-10-07 por la pasada de completitud (tarea `0002`, hallazgo F07), que sobrevivió a
> dos refutadores. Evidencia y comandos: bitácora 0043.

## De dónde sale

De los tipos que la plataforma acepta, `reauthFailure` y `reauthLockout` no tienen ningún emisor: `SessionLock` produce
el resultado y el shell descarta la razón. El canal parecería cableado el día que tenga URL y nunca reportaría un bloqueo
— el defecto exacto que ADR-0023 describe en LumeMed.

## Qué se hace

Inyectar el reporter donde nace el hecho (el lock o el shell), emitir por razón, y un test dirigido por el enum: todo tipo
traducible tiene un emisor o una razón escrita de por qué no. Corregir la fila F23 y §8.16, que dicen que sólo falta el
cableado.

## Qué NO hacer

- No emitir tipos que `PlatformSecurityEventKind` traduce a `null`: no salen igual.
