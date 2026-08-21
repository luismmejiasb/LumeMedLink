# 0020 · F20: la cadena de suministro, y la raíz de confianza equivocada

**Fecha:** 2026-08-21 · **Encargo:** F20, elegido porque cerraba un hueco que yo mismo había abierto.
Terminó encontrando siete.

## El que más impresiona: el gate admitía una API de datos clínicos

El allowlist hacía **coincidencia por prefijo sin frontera de namespace**. Consecuencia medida por
mí, no argumentada: **`androidx.health.connect` —una API de datos CLÍNICOS— pasaba en verde**, en la
app cuya identidad entera es que no toca contenido clínico. Junto con `io.ktorexploit` y
`org.jetbrainsevil`.

Y lo importante es **por qué mis cebos anteriores no lo vieron**: usaban nombres no relacionados
(`net.evil.tracker`). Un nombre hostil que **comparte namespace** con uno real es justo el que un
humano deja pasar en review — y era exactamente el que el gate no podía ver. *El cebo fácil prueba
lo fácil.*

Ahora la coincidencia es exacta, y `androidx.health` está en el denylist **por nombre, con su
razón**: una negativa que se explica sola vale más que una ausencia.

## La superficie de mayor privilegio no tenía ningún control

`allprojects { dependencyLocking { … } }` cubre configuraciones de proyecto. **El classpath de
plugins no aparecía en ningún lockfile**, así que el gate era estructuralmente incapaz de ver un
solo artefacto de ahí. Y un plugin de Gradle corre con los privilegios completos del desarrollador
y **puede reescribir el APK de salida**.

Al lockearlo aparecieron **24 grupos que nadie había revisado nunca**, entre ellos `org.tensorflow`
(sí, TensorFlow, por el soporte de ML model binding de AGP), `com.google.crypto.tink` y
`com.android.tools.utp`. El gate pasó de 441 a **500 módulos**.

## La raíz de confianza era el archivo equivocado

`distributionSha256Sum` pinnea la distribución de Gradle. Pero **el código que hace cumplir ese pin
es el propio `gradle-wrapper.jar`** — la raíz de confianza, y el único binario que este repo
trackea. Nadie lo miraba nunca.

Estaba mal. Verificado por mí contra los checksums publicados por Gradle: el jar commiteado era
**Gradle 9.4.1 auténtico**, mientras las propiedades pinneaban **9.7.1**. Lo heredé al copiar el
wrapper del repo gemelo. No es un ataque — pero un jar **cambiado** habría sido igual de invisible.
Regenerado, ahora coincide exacto con el checksum publicado de 9.7.1, y hay gate.

## Y cuatro más

- **Un comentario podía admitir un grupo.** El parser separaba por espacios, así que una nota
  indentada como `# transitive io.evil.beacon pulled in by ops` inyectaba ese grupo como entrada.
  Y el encabezado del archivo es prosa llena de nombres con puntos: la forma del exploit **es la
  práctica documentada**.
- **Borrar un lockfile dejaba el gate verde.** Fallaba sólo si no existía *ninguno*.
- **Dos doctrinas sin cumplimiento**: §7 prohíbe red fuera del stack y `org.apache.http.*` era
  importable; §8.1 exige una sola fachada de log y **`org.slf4j` viaja en el APK de release**
  (arrastrado por `kotlinx-coroutines-slf4j`) sin que nada lo prohibiera.
- **Las actions de CI iban por tag mutable**, sin bloque `permissions:`. Trampa al arreglarlo: el
  tag v4 de `gradle/actions` es **anotado**, así que el SHA del objeto tag **no** es el del commit —
  fijar el equivocado simplemente no habría resuelto.

## Lo que decidí NO hacer, y por qué

**Verificación de dependencias por bytes** (`verification-metadata.xml`) es lo único que pinnea
*bytes* en vez de coordenadas — y no la adopté. Cientos de entradas, costo en cada bump, y **no
cubre los dos descargables más grandes**: el toolchain de Kotlin/Native (~497 MB de LLVM) y el SDK
de Android.

Lo que lo revertiría está escrito en el ADR: que la app llegue a una tienda, o que CI produzca el
artefacto. **Hoy la cadena de suministro entera termina en un laptop** — y ese es el hueco más
grande de todos, más que cualquier swap de artefacto.

## Y una violación mía, dicha

Cité `ADR-0018` en seis lugares **antes de escribirlo**. El §10 pide el ADR en `docs/adr/` y el §13
lista «dependencia nueva sin ADR» como rechazo inmediato. Lo escribí antes de commitear, que es lo
mínimo, pero la citación adelantada es exactamente el tipo de deuda silenciosa que este repo
persigue en otros.
