# 0026 · F7 cerrado, y el host que llevaba dos semanas enlazando Kotlin viejo

**Fecha:** 2026-09-07 · **Origen:** F7, el único slice de la lista que no dependía de nadie.

## Lo que era la vulnerabilidad

El Keychain de iOS **sobrevive a que se borre la app**. El contenedor no. Así que borrar y
reinstalar dejaba a la instalación nueva encontrando los secretos de la anterior: una sesión heredada
entre dos personas que usan el mismo teléfono — lo que §8.13 promete que un logout impide, ocurriendo
sin logout. Y §8.17 dice que acá esa amenaza es **más** probable que en LumeMed, porque el teléfono
de un paciente lo usa la familia.

En Android no pasa: desinstalar borra el directorio de datos y las entradas de Keystore. Es la
asimetría más limpia de la lista, y el archivo de Android existe para **decirlo**, no para callarlo.

## Verificado de verdad, con control que funciona

| | con el sentinel | sin él (control en vivo) |
| --- | --- | --- |
| tras reinstalar (**la premisa**) | `PRESENT` | `PRESENT` |
| tras correr el guardián (**el arreglo**) | **`ABSENT`** | **`PRESENT`** |

La fila de la premisa es la que hace que el resto signifique algo. Y el control reproduce el agujero,
así que el guardián está **probado sostenedor**, no supuesto.

**Es la primera propiedad de seguridad iOS que este repo verifica de verdad.**

## El instrumento se equivocó primero, y era instructivo

La primera corrida dijo `launch B = ABSENT` y el script concluyó «la premisa no se sostiene». Era
falso: el launch A también era contenedor fresco, así que **el sentinel purgaba la semilla del propio
observador**. El instrumento estaba leyendo el éxito del guardián como su propio fracaso.

Se arregla con un lanzamiento extra —A2, contenedor ya marcado, que siembra sin que nadie purgue— y
la explicación quedó escrita en el script para que nadie la vuelva a deducir.

## Y entonces el control en vivo falló, y la causa no era el guardián

Sin el sentinel, el secreto desaparecía **igual**. Un verde de arriba que podía ser por la razón
equivocada.

Nada más llamaba a `wipe()`. Así que miré los tiempos:

```
fuente parcheada : 10:37:43
framework enlazado: 10:36:38   ← anterior al parche
```

**El host enlazaba Kotlin viejo.** La build phase corría
`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` **sin `KOTLIN_FRAMEWORK_BUILD_TYPE`**, y
sin esa variable el plugin de Kotlin imprime *«Unable to detect Kotlin framework build type»* como
**warning**, no refresca `build/xcode-frameworks`, y el enlazado usa lo que haya quedado ahí.

El build queda **verde** mientras la app corre código viejo. Es el peor tipo de fallo: silencioso, y
disfrazado de éxito.

## Lo que eso invalida, dicho sin adornos

**Toda observación de iOS entre el 2026-08-25 y el 2026-09-07.** En particular, el control de la
bitácora 0023 que «probó» que el simulador no puede verificar el cover de privacidad: ese control
desactivaba el cover **parcheando Kotlin**, y el parche nunca llegó al binario. Por eso «nada
cambiaba»: no cambiaba nada porque no se cambiaba nada.

La conclusión de ADR-0025 **se retira por no probada**. No se reemplaza por la contraria: intenté
volver a medirla con el build arreglado y quedó **inconcluso**. Afirmar ahora que el simulador sí
puede verificarlo sería cometer el mismo error en espejo.

Pista que sí quedó, para quien retome: la tarjeta del conmutador salía **oscura**, y el login es
blanco mientras el color del cover de Compose es `0xFF0E1116`. Eso sugiere que el cover de Compose
**sí** aparece en el snapshot — pero es una inferencia, no una medición, y así queda anotada.

## Los gates se equivocaron, y sus propios cebos los cazaron

Primera pasada: **cinco de seis cebos verdes**. Los chequeos de presencia se satisfacían con una
*mención* — la línea de `import`, o el propio comentario del gate dentro del `pbxproj` explicando la
variable que el gate exige.

Misma clase que las tres veces que le pasó a FLAG_SECURE. Se corrigió pidiendo la **llamada**
(`enforceInstallBoundary(`) y la **asignación** (`KOTLIN_FRAMEWORK_BUILD_TYPE=`), y stripeando
imports además de comentarios. Los ocho cebos ahora rojos, incluido uno nuevo: **leer la sesión antes
del guardián**, que compila, pasa los tests y deja el agujero abierto.

## Lección

Tres instrumentos fallaron en esta sesión antes que el código: la secuencia del verificador, la build
phase del host, y los gates. **El código estaba bien desde el principio.**

Vale la pena tenerlo presente: en este repo, cuando un control no reproduce lo que debería, la
apuesta correcta ya no es «el arreglo funciona mejor de lo que pensaba» sino «mi instrumento no está
midiendo lo que dice medir».
