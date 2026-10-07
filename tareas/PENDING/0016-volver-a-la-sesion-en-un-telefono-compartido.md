# 0016 · Volver a la sesión en un teléfono compartido

> **Estado:** PENDING · abierta el 2026-10-07 por la pasada de completitud (tarea `0002`, hallazgo F05), que sobrevivió a
> dos refutadores. Evidencia y comandos: bitácora 0043.

## De dónde sale

La biometría no distingue personas dentro del teléfono: el desbloqueo acepta **cualquier** huella o rostro enrolado
cuando se creó la clave, el de un familiar incluido. Lo que el tier compra es que un enrolamiento **nuevo** no herede la
sesión. Declarado en el threat model (T2) y en ADR-0011 (enmienda del 2026-10-07).

## Qué se hace

Decidir cómo se vuelve a la sesión cuando el teléfono es compartido: por ejemplo, re-autenticar con el IdP (contraseña +
TOTP) en vez de biometría cuando la persona lo declara, o avisar al enrolar que cualquier biometría del teléfono abre la
sesión. El hogar natural es el sucesor de ADR-0003 (política de sesión del tier paciente).

## Qué NO hacer

- No construir un PIN propio de la app: es autenticación casera (§8.2).
- No ofrecer el respaldo con credencial del dispositivo: la familia la conoce, y anula la invalidación del tier.
