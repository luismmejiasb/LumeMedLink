# 0011 · F17 — deep links y universal links

## De dónde sale

`docs/security/fortification-plan.md`, fila F17: la **única** tajada de fortificación en ⬜. **El lado cliente** no
depende de nada externo desde que el host iOS existe (2026-08-25); la verificación de dominio sí (ver
«Bloqueo real»).

## Lo que ya está

- La mitad **negativa**: el `Info.plist` **no** declara `CFBundleURLTypes` (custom schemes prohibidos,
  §8.12), y `check-ios-host.sh` lo exige — **sin cebo en `rehearse-gates.sh`**: sólo se ensayó una
  vez al crear el gate (ADR-0025), que es justo el ensayo que ADR-0029 dice que no cuenta. Agregarlo es
  parte de esta tarea.
- La doctrina (§8.12): sólo App Links verificados (Android) / universal links (iOS); identificadores
  opacos; **un link concede navegación, jamás acceso**; con la sesión bloqueada **ningún link reubica**.

## Qué se construye

La mitad positiva: el entitlement de associated domains y el `apple-app-site-association` (iOS), el
intent filter con `autoVerify` y el `assetlinks.json` (Android), y un router en `app/` que **pase por
el lock** antes de navegar.

## Bloqueo real

Los archivos de asociación viven **en un dominio**, y no hay dominio de producción (el tablero del
backend: no hay proyecto GCP). Se puede construir y probar el lado del cliente —el router y su relación
con el lock— antes; la verificación de dominio espera.

## Qué NO hacer

- No agregar un custom scheme «para desarrollo». En iOS el gate lo rechaza; **en Android no hay gate
  todavía** —un `<data android:scheme=…>` pasaría todos—, así que esta tarea lo agrega, con su cebo.
- No dejar que un link abra una pantalla con la sesión bloqueada, ni siquiera para mostrar el lock
  «encima».
