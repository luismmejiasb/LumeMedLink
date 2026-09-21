# 0030 — El cover que nunca armó

**2026-09-21.** Cuarto punto de la auditoría. El cover de privacidad de iOS —lo único que hay contra
el snapshot que el sistema toma al salir del primer plano, porque en iOS no existe `FLAG_SECURE`—
se armaba en `applicationWillResignActive`.

**Nunca armó. Ni una vez, desde el día que se escribió el host, el 25 de agosto.**

## El hecho de plataforma

En una app que adopta el ciclo de vida de **escenas**, UIKit **no llama** cuatro métodos del
`UIApplicationDelegate`: `applicationWillResignActive`, `applicationDidBecomeActive`,
`applicationDidEnterBackground` y `applicationWillEnterForeground`. Una app SwiftUI con
`WindowGroup` **es** de escenas, con o sin `UIApplicationSceneManifest` en el plist, y
`@UIApplicationDelegateAdaptor` no los devuelve.

Medido, con control positivo en el mismo delegate:

```
LUMEPROBE didFinishLaunching                     ← control: dispara
LUMEPROBE notif UIScene.willDeactivate           ← dispara
LUMEPROBE notif UIApplication.willResignActive   ← la NOTIFICACIÓN dispara
(ausente)  applicationWillResignActive           ← el MÉTODO no
```

Sin error, sin warning, sin crash. Código que se lee como control, compila, embarca y no hace nada.
**Y el gate lo EXIGÍA**: custodiaba código muerto.

## Por qué sobrevivió tres semanas de mirarlo de frente

1. **`UIApplication.willResignActiveNotification` se sigue publicando.** Sólo el *método* deja de
   llamarse. Quien razone «igual se entera» tiene media razón, por el canal donde nadie escuchaba.
2. **El overlay de Compose cubre los mismos píxeles hoy**, así que el fallo del otro era invisible
   desde fuera. La `UIWindow` del host existe para lo que Compose **no** puede tapar —una alerta, un
   share sheet, un prompt del sistema— y nada de eso existe todavía en esta app.
3. **Cada intento de medirlo estaba contaminado por otra cosa**: primero un framework Kotlin rancio
   (ADR-0028), después un enlace que no se refrescaba (ADR-0030), y encima una pantalla que se ve
   negra porque los placeholders dibujan texto negro sin fondo.

El veto de teclados de terceros —`shouldAllowExtensionPointIdentifier`, §8.10— **no** está en el
conjunto derogado y funcionó siempre. Son exactamente esos cuatro.

## El artefacto correcto

Bitácora 0023 concluyó que el simulador no puede verificar esto, porque la tarjeta del conmutador
sale en blanco **con los dos covers apagados**. ADR-0028 retiró esa conclusión por no probada.

Las dos discutían sobre **una tarjeta en una pantalla**. El artefacto está en disco:

```
Library/SplashBoard/Snapshots/sceneID:<bundle>-default/*.ktx
```

Un KTX de color plano comprime a una fracción de uno con contenido. **No hay que decodificarlo: el
tamaño, contra un control en vivo, es la medición.**

| condición | mayor snapshot de escena |
|---|---|
| producción, los dos covers | ~1,0–2,3 KB (plano) |
| **sólo la `UIWindow` del host** (overlay de Compose apagado) | ~1,0–2,3 KB (plano) |
| **ninguno de los dos** — el control | **7,3–10,6 KB (contenido)** |

Tres condiciones y no dos, porque **con dos covers uno tapa el fallo del otro**. La segunda fila es
la que importa: aísla la capa que llevaba tres semanas muerta.

Medido en dos simuladores distintos y dos versiones de iOS. `Scripts/verify-ios-privacy-cover.sh`.

## Qué se cambió

- El cover se arma desde `UIScene.willDeactivateNotification` y se retira con
  `UIScene.didActivateNotification`. La escena sale de la notificación, no de
  `connectedScenes.first`, que era orden arbitrario disfrazado de búsqueda.
- **El gate se invirtió**: exige las notificaciones de escena y **falla si alguno de los cuatro
  métodos derogados está implementado siquiera**. Nombrarlos hace que reintroducir uno sea una
  decisión y no una costumbre. Dos cebos nuevos; el ensayo pasa de 21 a 23.
- El KDoc de `PrivacyScreen.kt` decía, **en código**, lo que ADR-0028 ya había retirado. Corregido.

## Lo honesto sobre el riesgo que existió

**El overlay de Compose cubrió el contenido todo el tiempo**, y hoy no hay ninguna pantalla que
presente algo por encima. Lo que faltaba es la capa que va a importar el día que exista un prompt
biométrico, una alerta o un share sheet — o sea, el end-to-end de F4, a un slice de distancia.

No fue una brecha. Fue un control apagado esperando a que hiciera falta.

## La regla, más ancha que este bug

**Un control de seguridad que depende de un callback del sistema necesita una medición que demuestre
que el callback LLEGA.** Compilar no es evidencia. Un `NSLog` con control positivo cuesta un build.

**LumeMed no está afectado** (verificado por grep hoy): no implementa ninguno de los cuatro y observa
`scenePhase`. La lección corre al revés — el host nuevo reintrodujo un patrón que la app madura ya
había evitado.

ADR-0031. F1 cerrado en las dos plataformas.
