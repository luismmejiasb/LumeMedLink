# 0026 · Reentrada por SMS y correo en un teléfono sin hardware biométrico

> **Estado:** FREEZE · abierta el 2026-10-07 con la decisión del autor (ADR-0037, enmienda del mismo día). Congelada
> porque necesita un backend desplegado: el envío de los códigos no es de la app.

## De dónde sale

El autor: ninguna app Lume opera sin reautenticación biométrica; **si el celular no tiene esa capacidad, se valida por
SMS y correo.** Hoy `establishSession` rechaza la sesión (`TIER2_UNAVAILABLE`).

## Qué se construye

1. Distinguir **sin hardware** (permanente: `BIOMETRIC_ERROR_NO_HARDWARE`, `LAError.biometryNotAvailable`) de **sin
   enrolar** (se pide enrolar, no se ofrece el respaldo). La función que clasifica vive en `core/session` con su test,
   como `unlockOutcomeForPromptError`.
2. Una segunda implementación de `UnlockGate` que reentra con **dos códigos, uno por SMS y otro por correo**, emitidos y
   verificados por el IdP o el backend. El «material de clave» de ADR-0011 pasa a ser la respuesta del servidor: el
   desbloqueo sigue sin ser un booleano local.
3. El mismo techo de intentos (ADR-0034), y los mismos eventos de seguridad (tarea `0018`).

## Qué espera

- Del backend: el envío del código por correo, y la verificación de ambos (pedido nuevo en `docs/backend-requests/`).
- De Identity Platform: SMS como segundo factor para esta audiencia.

## Qué NO hacer

- Generar o verificar un código en el teléfono: autenticación casera (§8.2).
- Ofrecer el respaldo a un teléfono que tiene sensor y no tiene huella enrolada.
- Un solo canal: con uno solo, el respaldo vale menos que la biometría que reemplaza.
