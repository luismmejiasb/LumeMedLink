# 0017 · iOS: tapar la grabación de pantalla y la duplicación por AirPlay

> **Estado:** DONE (2026-10-07; la mitad de la captura sin medir en dispositivo) · abierta el 2026-10-07 por la pasada de completitud (tarea `0002`, hallazgo F06), que sobrevivió a
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

## Cerrada — 2026-10-07 (la captura, sin medir en el simulador; declarado)

El host observa `UIScreen.capturedDidChangeNotification` y cubre **todas** las escenas mientras iOS dice que hay una
captura (grabación, duplicación por AirPlay); al volver al primer plano **no** descubre si la captura sigue; al terminar,
descubre sólo una app que está al frente. `UIScreen.isCaptured` y no `sceneCaptureState`, porque éste empieza en iOS 17 y
el host apunta a 16. Gate en `check-ios-host.sh` con dos cebos (dejar de observar; descubrir al activarse aunque haya
captura), los dos rojos.

**La medición, y lo que dijo.** Con NSLogs temporales en el host, dos veces (la segunda con el Kotlin fresco):
`simctl io recordVideo` **no** dispara `capturedDidChange` — el log de arranque (control positivo) y el de la cubierta al
salir de la app (control del camino de la ventana) sí aparecen. O sea: **el simulador no reproduce `isCaptured` con
`recordVideo`**, como la tarea sospechaba, y la mitad de la captura queda **sin medir**: se mide en un iPhone, con la
grabación del Centro de control. El evento `screenCaptureDetected` tampoco se emite todavía: el canal vive en Kotlin y
este observador en Swift; se cablea cuando el canal tenga URL.
