# 0032 — Los gates que quedaron nombrados

**2026-09-21.** ADR-0029 cerró dos gates críticos y **escribió una lista de los que no cerraba**.
Esta bitácora es esa lista, cerrada. Seis gates, cada uno reproducido con cebo **antes** de tocarlo.

Lo digo así porque es el punto: *«cerrar algo no cierra lo que lo pedía»* tiene un converso barato —
**nombrar por escrito lo que no arreglaste es lo que hace que se arregle**. Nada recomputa la prosa,
pero una lista con nombres propios sí se puede leer y tachar.

## La frontera de datos veía una propiedad y nada más

Sólo hacía match después de `val`, `var` o `const val`. Cuatro cebos, los cuatro Kotlin corriente:

```kotlin
fun render(motivoClinico: String)        // parámetro de función
enum class Reason { DIAGNOSIS_FOLLOW_UP } // entrada de enum
class AllergyBanner                       // nombre de tipo
typealias Prescription = String           // alias
```

Los cuatro verdes. Enumerar posiciones sintácticas es el mismo juego perdido que nombrar grafías
malas — sólo caza las que a alguien se le ocurrieron. Ahora la regla es total: una palabra clínica no
aparece como identificador **en ninguna posición**. Verificada verde contra el árbol de hoy antes de
adoptarla, que es lo que vuelve segura una regla así.

Y **`motivo` faltaba del vocabulario** — el campo que §1.0 nombra a mano: «Nunca el motivo clínico».

## El wrapper afirmaba por presencia lo que debía afirmar por valor

`grep -q '^distributionSha256Sum='` pasa con la línea **vacía** y con un hash de **ceros**.

Ahora está fijado **por valor** en el script —obtenido hoy de la publicación de Gradle y comparado— y
tiene que concordar con el del archivo: dos lugares que deben coincidir.

**Y de paso apareció uno que nadie había nombrado: no se verificaba el HOST.** La comprobación de
versión sólo mira el *nombre del archivo*, así que
`https://evil.test/distributions/gradle-9.7.1-bin.zip` la satisfacía — este build descargaría y
**ejecutaría** un Gradle de otro sitio. Cinco cebos ahora.

## El pineo de acciones era una denylist de formas de tag

`v?[0-9]…|main|master`. Entraban `@latest`, `@develop`, `@release`, `@HEAD` y cualquier tag no
numérico. Invertido a **allowlist**: SHA de 40, tag opcional como comentario, o falla — incluidas las
formas que todavía no existen.

## Un paquete `core` bajo `androidApp` estaba exento de todo sin ser core

La regla I5 sólo recorría `composeApp/src`, y P3/P4 excluían **cualquier** ruta con `/core/`.

Medido: un `getSharedPreferences(...).putString("t", token)` en
`androidApp/src/main/kotlin/com/luismejias/lumemedlink/core/` pasaba los dos gates. I5 ahora recorre
los dos módulos y la exención está **anclada**: el core canónico es un directorio, en un módulo.

## Y el que más me preocupó: un token a logcat desde `core/`

detekt exime `core/` del `ForbiddenImport` — **tiene que hacerlo**, ahí viven Ktor y OkHttp
legítimamente. Así que `import android.util.Log` estaba permitido. Y entonces la llamada se escribe
`Log.d(...)`, que el patrón **cualificado** de `check-logging.sh` no veía.

**Las dos mitades apagadas, justo en `core/session`, que es donde viven los tokens.** Medido con
cebo, con CI en verde.

Ahora se afirma la forma corta —sin disparar con `LumeLog.`, que es el sink propio— y el **import**,
sin exención. Este gate es el total; el carve-out de detekt es sobre **red**, no sobre **logs**, y
heredarlo fue el error.

## La mitad iOS de las superficies pre-auth no estaba escrita

Todo `check-preauth-surfaces.sh` recorría `composeApp/src androidApp/src`. Una
`UNUserNotificationCenter`, un widget, un `NSUserActivity` o un `UserDefaults` **en Swift** pasaban
sin tocar nada — y el `UserDefaults` de Swift tampoco lo veía P3, que es Kotlin. La regla existía
completa en la doctrina y a medias en el código.

## El saldo

El ensayo pasa de **15 cebos a 35**. Todos rojos.

Y el patrón que atraviesa los seis, que no es «el autor se equivocó»: **cinco de los seis eran gates
correctos con el alcance mal puesto.** Miraban la posición sintáctica equivocada, el módulo
equivocado, la mitad del campo, una plataforma de dos. La pregunta que los habría cazado a todos no
es *«¿está bien la regla?»* sino **«¿sobre qué exactamente corre, y qué queda justo afuera?»**.

ADR-0029, segunda pasada.
