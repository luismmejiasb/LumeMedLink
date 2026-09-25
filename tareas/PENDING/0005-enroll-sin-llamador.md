# 0005 · `enroll()` no tiene ni un llamador: el material del tier 2 nunca se crea

## De dónde sale

Auditoría del 2026-09-20/21, dimensión biometría (sobrevivió 2 de 3 refutadores). `UnlockGate.enroll()` crea
la clave del tier 2 —la que el desbloqueo biométrico firma—, y **nada en producción la llama**:
`grep -rn '\.enroll()' composeApp/src/{commonMain,androidMain,iosMain} androidApp/src` → nada.

## Por qué importa

El día que la app llegue a `Locked` (hace falta login), `attemptUnlock()` le pedirá al gate que firme
con una clave **que no existe**. Dependiendo de la plataforma eso es `Unavailable` o `Invalidated`, y
las dos terminan la sesión: el médico **nunca** podría desbloquear con biometría. El tier 2 está
construido, probado en device, con su invalidación medida — y desconectado del flujo.

## Qué se hace

Decidir **dónde** se enrola: el candidato natural es justo después de `SessionManager.establish()` en
el flujo de login (S1.1), porque es el único momento con credencial fresca. Esa decisión es parte del
slice de login, **no se toma aquí**.

## Qué NO hacer

- No enrolar al arrancar ni desde `probeSession`: enrolar sin una autenticación reciente es exactamente
  lo que el anclaje a material de clave (ADR-0011) existe para evitar.

## Cómo se verifica

Un test de `SessionLock`/login que falle si, tras establecer sesión, el gate no fue enrolado. Con cebo:
quitar la llamada lo pone rojo.
