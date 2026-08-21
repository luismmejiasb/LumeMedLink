# 0008 · F1: captura de pantalla y multitarea

**Fecha:** 2026-08-21 · **Encargo:** primer slice de fortificación, Fase A. El autor eligió empezar
por Fase A sabiendo (del plan aprobado) que parte necesita el shell.

## El ataque, en lenguaje humano

Alguien tiene tu teléfono un momento — un familiar, alguien en la sala de espera. Sin desbloquear
nada puede: tomarle **una foto a la pantalla** con otro teléfono, o abrir el **conmutador de apps
recientes**, donde el sistema guardó una **miniatura** de lo último que mostraste — y ahí queda
"Dr. Fulano, Oncología, martes 10am". Eso, para esta app, no es un detalle: la agenda **revela
información de salud** (la amenaza T2 del threat model, la primera en rango). Aparte, en Android una
app maliciosa puede dibujar algo **encima** de la tuya para robar tus toques (tapjacking).

## Qué hice

- **Android — `FLAG_SECURE`, puesto una vez en el Activity, para toda la app.** Bloquea el
  screenshot y **deja en blanco la miniatura** de recientes. App-wide y no por-pantalla porque
  **toda** esta app es dato-personal-adyacente (ADR-0010): así ninguna pantalla puede olvidarlo.
  Más `filterTouchesWhenObscured`: los toques que llegan mientras otra ventana tapa la nuestra se
  descartan (tapjacking).
- **iOS — un cover de privacidad**, porque iOS **no tiene** API para bloquear screenshots (la
  asimetría 1 del threat model): la protección es **tapar el contenido antes de que el sistema
  fotografíe** la pantalla para el conmutador. Construí la primera capa: un overlay opaco en la capa
  Compose, manejado por el ciclo de vida, que aparece cuando la app no está RESUMED. Sin blackout
  fingido (LumeMed ADR-0023) — es una superficie opaca real.
- **Gate `check-screen-security.sh`**: verifica que FLAG_SECURE y el guard de tapjacking **existan**
  en el shell (presencia) y que nadie los quite ni meta una API de captura de pantalla (ausencia).
  Corre en CI.
- **Test**: la lógica `shouldCover` pinneada en cada estado del ciclo de vida, en ambos targets.

## Lo que el ensayo con cebo atrapó (por esto es obligatorio)

**El gate nació con dos huecos, y los cuatro cebos los delataron:**

1. **Presencia foolable por comentario.** El primer check era `grep FLAG_SECURE` a secas. El
   **comentario** de MainActivity dice "FLAG_SECURE app-wide", así que borrar la llamada real y
   dejar el comentario **pasaba verde**. Peor: el `AndroidManifest.xml` tenía otra mención en un
   comentario XML (`<!-- … FLAG_SECURE … -->`) que también colaba. Fix: la presencia solo cuenta en
   líneas **no-comentario de archivos `.kt`**. Con eso, borrar el `setFlags` real (dejando el
   comentario) por fin da rojo.
2. **Mi propio harness falló primero.** Los cebos 2 y 3 los escribí a un directorio que no existía
   → el archivo no se creó → el gate no vio nada → falso verde **de mi lado**. Recién con `mkdir`/
   ruta existente los cebos de PixelCopy y `clearFlags(FLAG_SECURE)` dieron rojo. La lección de la
   familia otra vez: un gate que no se vio fallar no existe — y quien lo ensaya puede equivocarse al
   ensayarlo.

De paso, el comentario del manifiesto quedó corregido: decía que FLAG_SECURE llegaba en S1.2;
llegó aquí, en F1.

## Lo diferido, declarado (no escondido)

- **El cover iOS robusto** es una `UIWindow` de host mostrada en `willResignActive` de la escena —
  necesita el shell iOS (proyecto Xcode) que no existe. El overlay Compose es la capa disponible sin
  ese host, y ataja el caso común; la capa de host se cablea cuando exista el shell.
- **Verificación en device**: que la miniatura salga en blanco de verdad y que el cover aparezca en
  el momento exacto son **chequeos en device** (S1.2), no aserciones de host-test. Lo que sí está
  testeado hoy: la lógica del cover en ambos targets.
- El color del cover es un primitivo funcional (opacidad), no un token de diseño; lleva un neutro
  mínimo hasta que S0.3 dé una superficie de theme.

## Estado

F1 🟡 en el plan (núcleo hecho, cover iOS robusto diferido al shell). Batería completa verde: build
ambos targets, tests iOS+Android, detekt, ktlint, y los cuatro gates de script.
