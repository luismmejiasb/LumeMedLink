# 0036 — La re-verificación en la Mac corporativa, y tres instrumentos que no podían decir la verdad

**Tipo:** `verificación` + `scripts` · 2026-10-07

## Qué llega

- **Tarea `0001` cerrada** (`tareas/DONE/0001`): todo lo que corrió verde en el equipo viejo, re-corrido en esta máquina,
  con cada diferencia contra los valores de referencia explicada en su cierre.
- **Tres verificadores arreglados**, cada arreglo con su control: `verify-install-sentinel.sh`,
  `verify-ios-privacy-cover.sh` y `verify-tier2-invalidation.sh`.

## La máquina

Es la misma Mac corporativa donde se hizo la auditoría, pero **no es el mismo entorno**: los repos se mudaron de
`~/Documents/iOS/Projects/` a `~/Documents/Lume/` (regla de aislamiento del autor), Xcode pasó a 27.0 con SDK de iOS 27.0,
el AVD `Pixel_9` volvió sin PIN y sin huella, y el árbol trae commits que nunca compilaron en ninguna máquina (la S0.3 se
escribió en una sesión sin red a `dl.google.com`). Un verde de antes no decía nada de eso.

Cómo se corrió, para que la próxima sesión no lo redescubra: `ANDROID_HOME` lo pone el perfil de la shell (sin
`local.properties` también compila; se creó igual, ignorado por git); el emulador se fija con `ANDROID_SERIAL` porque el
dispositivo fantasma `Ucamera001 offline` sigue en esta Mac; cada simulador es propio (`lume-sim` más dos creados a mano
para iOS 26.5 y 18.6); y los `xcodebuild` que lanzan los scripts pasan por el turno de build de la máquina con un shim en
el `PATH`, porque el hook sólo ve la línea de comando que se tipea.

## Lo que corrió verde

Gradle (detekt, ktlint, `build`, iOS): 124 tests en Android host; 130 en iOS, de los cuales 6 se saltan y 124 corren —
igual que la referencia. Los 18 gates y la mitad fusionada del de red. El ensayo: 40 cebos, todos rojos. El host de iOS
compila. `verify-ios-link-freshness`: PRESENT con la guarda, ABSENT sin ella. `verify-ios-privacy-cover` en iOS 27.0,
26.5 y 18.6: OK en los tres. `verify-install-sentinel` con control en vivo: OK. `verify-no-backup` con control en vivo:
OK (sin la sección, D2D vuelve a extraer). `verify-tier2-invalidation`: probado. Instrumentados: 12 de 12.

## Los tres instrumentos

**1. El sentinel no podía pasar desde el 2026-09-21, y habría culpado a la premisa.** Su observador se inyecta en
`AppDelegate.swift` anclado al *cuerpo* de `didFinishLaunching` (`-> Bool { return true }`). Ese cuerpo dejó de existir
cuando ADR-0031 le dio uno de verdad, y el `assert` que fallaba se ignoraba: el archivo del observador se escribía, se
listaba en el proyecto y **no se llamaba nunca**. Cada lanzamiento habría leído `NO-FILE`, y el veredicto habría sido
«la premisa no se sostiene» — rojo, por la razón equivocada, apuntando al Keychain cuando lo roto era el instrumento.
Ningún registro del repo dice que se haya corrido después de ese commit (su última corrida registrada es la de F7, el 2026-09-07), y por eso nadie lo vio. Ahora el ancla es la *firma* del método, y si el observador
no se puede instalar el script lo dice como fallo del instrumento y se detiene.

Dos defectos más en el mismo script. Su `trap` deshacía dos de sus cuatro ediciones: un build fallido a mitad de corrida
borraba el observador y dejaba el `pbxproj` listando un archivo que ya no existía. Y tomaba el `.app` del DerivedData
compartido con `find …/iosApp-*/… | head -1`, en una máquina donde el mismo proyecto vivió en otra ruta: podía instalar un
build viejo y medirlo. Ahora usa DerivedData propio y deshace todo en cualquier salida.

**El control también mintió primero.** Para forzar la falla del build usé un `DEVELOPER_DIR` inexistente, y el árbol
quedó limpio con el script viejo — parecía que el defecto no existía. Era que `/usr/bin/python3` es un shim de `xcrun`:
sin toolchain no corrió ninguna de las ediciones. El control válido fue un `xcodebuild` falso en el `PATH`, que además
anotó el estado del árbol *durante* el build: con el script viejo, `delegate_calls=0` (la inyección fallida) y el
`pbxproj` sucio al salir; con el nuevo, `delegate_calls=1` y el árbol limpio.

**2. Los dos verificadores de iOS elegían «el primer simulador encendido».** Con varias sesiones en la misma Mac, ése
puede ser el simulador de otro repo, y estos scripts instalan, desinstalan y siembran Keychain en lo que elijan. Ahora
aceptan `--device` y, si hay más de uno encendido, se niegan a adivinar.

**3. El asistente de huella del tier 2 se manejaba con `sleep` fijos.** Primera corrida: falló con Play Store en primer
plano — los toques cayeron en el dock del launcher. La causa exacta no se aisló (había un build de iOS al lado y la
máquina cargada). Ahora espera cada pantalla por su foco y exige que el conteo de huellas suba. Segunda corrida: falló
con precisión, atascada en la pantalla de captura — y esa causa fue mía: en la prueba manual había enrolado el dedo
virtual `7`, el mismo id fijo que usaba el script, y un dedo ya enrolado no avanza. Ahora cada corrida usa un dedo
nuevo. Tercera: el asistente no abrió porque la corrida anterior había dejado su tarea a medias; ahora se lanza en una
tarea limpia. La cuarta lo probó.

## Lo que no se verificó

- API 26–29: no hay imagen de esas versiones en esta máquina (la única es `android-37.1`). Lo que la tarea `0010` dice de
  `UnlockKeyContractTest` en esas versiones sigue sin medir.
- Un warning del enlazador de iOS, observado y no investigado: un objeto de ICU que trae Compose viene compilado para
  iOS-simulator 18.5 mientras la app se enlaza para 16.0. El build pasa; qué significa para un iPhone con iOS 16 o 17 no
  se sabe.
