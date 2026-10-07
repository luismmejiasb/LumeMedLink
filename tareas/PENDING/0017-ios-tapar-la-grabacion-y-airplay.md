# 0017 · iOS: tapar la grabación de pantalla y la duplicación por AirPlay

> **Estado:** PENDING · abierta el 2026-10-07 por la pasada de completitud (tarea `0002`, hallazgo F06), que sobrevivió a
> dos refutadores. Evidencia y comandos: bitácora 0043.

## De dónde sale

En Android, FLAG_SECURE ennegrece también la grabación y la transmisión. En iOS el cover sólo se arma al perder el foco:
una grabación de pantalla o una duplicación por AirPlay muestra la agenda. iOS sí ofrece cómo enterarse
(`sceneCaptureState` / `isCaptured`), y LumeMed lo usa (`ScreenSecurityMonitor`). Declarado en el threat model,
asimetría 1.

## Qué se hace

Portar el observador de captura a la ventana de cover del host (todas las escenas), con gate y cebo en
`check-ios-host.sh`, y medirlo: el verificador del cover sirve de modelo.

## Qué NO hacer

- No asumir que el simulador reproduce `isCaptured` con `simctl io recordVideo`: medirlo con control, o declararlo.
