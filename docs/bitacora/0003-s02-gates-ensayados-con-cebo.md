# 0003 · S0.2: los gates existen, y cada uno se vio rojo antes de creerle su verde

**Fecha:** 2026-08-20 · **Encargo:** segundo paso del handoff — detekt + ktlint + los gates custom
de la constitución §9, cada uno ensayado con archivo-cebo. La lección fundante de la familia,
aplicada literal.

## Lo que quedó montado

- **detekt 1.23.8** (`config/detekt/detekt.yml`, `buildUponDefaultConfig = false` como el gemelo):
  subset con peso constitucional — `GlobalCoroutineUsage`, `ForbiddenImport` (okhttp3, ktor,
  HttpURLConnection, SharedPreferences, DataStore, Firebase, Sentry, Coil/Glide/Picasso) con
  `core/` como única excepción de zona, `ForbiddenComment`, y los básicos de bugs potenciales.
- **ktlint 14.2.0** (`.editorconfig` espejo del gemelo, estilo intellij_idea, Composable exento del
  naming).
- **Tres scripts en `Scripts/`** (sh puro, sin dependencias): `check-feature-isolation.sh`
  (ADR-0008: I1 cross-feature, I2 dirección, I3 expect/actual sólo en core, I4 nadie importa app),
  `check-forbidden-patterns.sh` (P1 GlobalScope total, P2 document delivery — ADR-0007, P3 storage
  plano fuera de core) y `check-dependency-allowlist.sh` (lockfiles contra
  `config/dependency-allowlist.txt`; denylist con razones gana sobre el allowlist).
- **CI** corre los tres scripts antes de compilar, luego `detekt ktlintCheck`, luego el build.
- Etiquetas del §13 migradas: [lint] donde aterrizó gate, [manual] donde no — `no_mutable_object` y
  `no_hardcoded_style` siguen [manual] (exigen regla detekt compilada / design system) y así lo
  declara el yml.

## Lo que el ensayo con cebo atrapó (por esto es obligatorio)

1. **El gate de dependencias nació ciego y el cebo lo delató.** El `find` que juntaba los lockfiles
   combinaba dos `-maxdepth` con `-o`; en BSD find `-maxdepth` es opción GLOBAL, así que la
   búsqueda quedó en profundidad 1 y **los lockfiles de los módulos jamás se leyeron** — una línea
   `io.sentry` inyectada pasó verde. Tras el fix (dos `find` separados), el mismo cebo dio los dos
   rojos esperados (denylist + grupo desconocido) y la corrida limpia pasó de 69 a **332 módulos
   revisados**. Un gate que no se vio fallar no existe: éste existía y no miraba.
2. **`import okhttp3.*` (wildcard) se le escapa a `ForbiddenImport`** — el glob no matchea la forma
   wildcard del import. Cobertura real por capas: ktlint prohíbe wildcard imports categóricamente
   (visto rojo), y el import directo `okhttp3.OkHttpClient` sí dispara detekt (visto rojo). Se
   documenta la grieta en vez de fingir que no está.
3. **Mi propio script tenía el mismo defecto que vigila**: con el árbol sin `core/`, `grep -r` sin
   paths escaneaba el repo entero y acusaba imports legítimos del shell. Arreglado y re-ensayado.
4. El resto del tablero de cebos: GlobalCoroutineUsage ✓ (funciona sin type resolution — se dudaba),
   ForbiddenImport ✓ (SharedPreferences/DataStore/ktor/Firebase por la misma mecánica),
   ForbiddenComment ✓, ktlint (unused/wildcard/indent) ✓, I1–I4 ✓, P1–P3 ✓.

## El golpe de toolchain que no estaba en el handoff

detekt 1.23.8 revienta con el daemon de Gradle en JDK 26: su compilador Kotlin embebido no parsea
`"26.0.1"` (`JavaVersion.parse` lanza, el task muere con un críptico `> 26.0.1`). El `jvmToolchain`
del handoff pinnea las COMPILACIONES, no el daemon. Fix: **daemon JVM criteria**
(`gradle/gradle-daemon-jvm.properties`, `toolchainVersion=17`), escrito a mano porque
`updateDaemonJvm` exige el resolver de foojay que no queremos como dependencia. Aplica igual al
gemelo — nota para el vault.

## Decisiones de diseño del slice, dichas

- **`ForbiddenMethodCall` no se usa**: requiere type resolution y el task plano de detekt jamás la
  tendrá — la regla quedaría verde para siempre, un gate muerto. Las prohibiciones con forma de
  llamada viven en el script (grep), que es más tosco (marcaría la palabra en un comentario) y ese
  costo se acepta: un rojo falso se reescribe; un verde falso embarca una violación.
- La excepción de zona de `ForbiddenImport` es `**/core/**` completa, no sólo `core/networking`:
  una sola zona, revisada por constitución + ADR, en vez de dos globs que divergen. El allowlist de
  Gradle sigue vigilando qué ENTRA al classpath aunque core lo importe.
- `MainViewController` conserva PascalCase con supresión puntual: es la superficie que Xcode
  consume y la convención CMP del entry point iOS.

## Estado

`./gradlew build detekt ktlintCheck` + los tres scripts: **todo verde, sin cebos en el árbol.**
S0.2 cerrado. Sigue: el espejo de LumeNetworking en `core/networking` (ADR-0004).
