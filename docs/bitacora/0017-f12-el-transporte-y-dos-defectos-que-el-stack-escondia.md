# 0017 · F12: el transporte — y dos defectos que mi propio stack escondía

**Fecha:** 2026-08-21 · **Encargo:** F12, elegido precisamente porque arrastraba un defecto vivo.
Trabajado con cinco ángulos adversariales en paralelo antes de tocar código.

## Los dos defectos, en lenguaje humano

**1. En iOS, el token de sesión se escribía a disco en claro.**

NSURLSession trae una caché en disco por defecto, y NSURLCache guarda **las cabeceras junto con la
respuesta**. Nuestro stack adjunta `Authorization: Bearer …` en cada request. Consecuencia: el
primer GET real en iOS habría escrito, sin cifrar, en `Library/Caches`, tanto la agenda como **el
token de sesión** — y ahí se queda, porque el logout borra el Keychain y no sabe que existe una
caché de URL.

Lo que lo hacía difícil de ver: Ktor **ya** pone `NSURLRequestReloadIgnoringLocalCacheData` en cada
request, y eso *parece* "caché apagada". No lo es: esa política suprime las **lecturas**. Las
escrituras siguen. Sólo quitar la caché detiene la escritura. Y sin cabeceras `Cache-Control`, un
GET HTTPS **sí** se guarda.

**2. El log "redactado" conservaba el path completo.**

El sink quitaba el query y guardaba el path. Suena cuidadoso. Pero **las rutas de esta app llevan el
identificador EN el path** — `/v1/orgs/{tenantId}/patients/{patientId}/appointments` es la forma que
el backend ya publica. O sea: cada línea de log habría llevado el identificador de un paciente.
Justo la fuga que el sink existía para evitar.

## Qué hice

- **iOS**: la caché se apaga en una costura con nombre, `applyLumeCachePosture()`. La línea que
  carga el peso es `setURLCache(null)`; la política se queda al lado porque es la que un lector
  confunde con el control completo.
- **Redacción por construcción**: el constructor de `NetworkLogEntry` es privado y `of()` es la
  única puerta — redacta al entrar. Y es una **allowlist**: se conservan palabras de ruta y
  versiones (`v1`), todo lo demás se vuelve `{id}`. Un segmento que no reconozco se redacta, en vez
  de colarse porque nadie lo agregó a una lista de cosas que ocultar.
- **Android le declara su postura al sistema**, cosa que nunca hacía: cleartext negado **y anclas de
  confianza sólo del sistema**. Esa segunda mitad compra algo nuevo: una CA instalada por un MDM, un
  proxy corporativo o malware **no** intercepta este tráfico en Android.
- **`INTERNET` lo declaramos nosotros**, aunque ya llegaba por fusión desde `okhttp-android`.

## La afirmación falsa que llevábamos tres documentos repitiendo

La constitución §7, la ADR-0004 y el threat model decían que el manifiesto negaba cleartext.
**No existía**: ni `usesCleartextTraffic` ni `networkSecurityConfig` en ningún archivo. Corregidos
los tres en el mismo cambio que lo vuelve cierto — que es la regla, no un detalle.

## El gate que lee un archivo distinto a todos los demás

`check-network-posture.sh` verifica el manifiesto **FUSIONADO**. Los ocho gates previos leen el que
escribimos nosotros, y por eso son **ciegos a lo que inyectan las dependencias**. Eso no es
hipotético: encontré con el reporte de blame que `okhttp-android` inyecta `INTERNET` y
`androidx.biometric` inyecta `USE_FINGERPRINT` (deprecado) — **dos permisos que nadie en este repo
decidió**. El gate lleva una allowlist de permisos sobre el conjunto fusionado y, cuando falla,
**nombra la librería culpable**.

También rechaza `network_security_config_debug.xml`, un archivo hermano que la plataforma
**auto-descubre** en builds debug aunque nada lo referencie.

7 cebos, todos rojos — incluido uno que sólo este gate puede ver: una dependencia agregando
`READ_CONTACTS`.

## Otra vez: mi cebo estaba mal, no el gate

El cebo de la allowlist de permisos dio verde. Antes de "arreglar" el gate lo depuré: mi `sed`
usaba un ancla `$` sobre una línea que termina en comilla, así que no borraba nada. Rehecho el
cebo, el gate da rojo y hasta nombra la librería. **Tercera vez que reviso un verde sospechoso en
vez de creerle.**

Y una recurrencia que ya es patrón: mis greps volvieron a leer **el texto de un comentario** como si
fuera configuración — el comentario del XML explica qué haría `<debug-overrides>` y el gate lo leyó
como que estaba puesto. Van tres gates en los que pasa lo mismo. Ahora despoja comentarios antes de
comparar.

## Lo que sigue sin verificar, dicho fuerte

**El stack de red nunca ha abierto un socket real.** Sus 13 tests usan MockEngine, y
`platformHttpEngine()` —el engine de verdad— **no tiene ningún llamador en el árbol**. Lo verificado
es su lógica, jamás su transporte. Cambia cuando S1.1 cablee un cliente real.

Y un test en device **no puede** observar la negación de cleartext del sistema: el `require` de
Kotlin dispara primero. Probar esa capa exige un cliente construido a propósito alrededor del stack.

**iOS entero sigue sin verificar**: sin host no hay Info.plist, ni evidencia de ATS, ni forma de
observar la caché en un dispositivo. El fix compila y las pruebas de principio son de sondas
externas, no de esta app.

## Cuarta asimetría de plataforma, ahora en el threat model

**iOS confía en las CAs raíz que instala el usuario; Android no.** En Android el
`networkSecurityConfig` ancla la confianza al sistema. En iOS, un perfil con una CA puede
interceptar, y **lo único que lo cerraría es el pinning**, que ADR-0004 difirió. Mientras siga
diferido, el lado iOS es interceptable por quien controle el dispositivo. Estaba fuera del modelo.

## Registrado, no cerrado

Ambos engines honran en silencio el proxy del sistema; `androidx.emoji2` baja una fuente por Play
Services **fuera del stack** (y el allowlist de dependencias no puede verlo); y los metadatos de TLS
(SNI, tamaños) siguen revelando con qué host se habla.
