# 0024 · La ADR de FCM acotado

> **Estado:** PENDING · abierta el 2026-10-07 al aterrizar la decisión del autor del 2026-09-26 (ADR-0036, punto 3):
> FCM para Android, junto con APNs.

## De dónde sale

La constitución prohíbe Firebase y Play Services (§8.1, denylist de `check-dependency-allowlist.sh` y `ForbiddenImport`), y
el push de Android sólo existe vía `com.google.firebase:firebase-messaging`. El autor decidió FCM; lo que falta es la
excepción escrita y sus controles, antes de que un artefacto de Firebase entre a un lockfile. Las condiciones de partida
están en el vault (revisión legal del 2026-10-04, RL-13) y en la tarea `FREEZE/0022` del backend.

## Qué se construye

Una ADR que levante la prohibición **sólo** para `firebase-messaging`, con: analítica apagada, carga útil sólo de datos
(ADR-0012: el texto lo pone la app), token registrado después del login y borrado en el logout, y Google declarado como
subencargado. En el mismo cambio: el denylist estrecho al grupo exacto, el permiso de FCM en la allowlist del manifiesto
fusionado (`check-network-posture.sh` ya lo vería), y los gates de superficies pre-login enmendados para el camino de push.

## Qué NO hacer

- No agregar la dependencia antes de la ADR y sus gates.
- No dejar entrar Analytics, Crashlytics ni el resto de Play Services por la misma puerta.
