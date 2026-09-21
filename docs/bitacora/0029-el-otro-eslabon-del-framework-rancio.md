# 0029 — El otro eslabón del framework rancio

**2026-09-21.** Tercer punto de la auditoría. F7 encontró, el 7 de septiembre, que el host iOS
llevaba dos semanas enlazando Kotlin viejo: la build phase no fijaba `KOTLIN_FRAMEWORK_BUILD_TYPE`.
Se arregló y se puso gate.

**Se arregló el eslabón equivocado — o mejor dicho, sólo el primero.**

## Lo medido

Sobre código limpio, un cambio de **un solo literal de Kotlin** y nada más:

```
Gradle regeneró el framework, y el literal nuevo ESTÁ dentro
El binario de la app:  mismo sha256, mismo mtime, literal AUSENTE
xcodebuild:            BUILD SUCCEEDED
```

`KOTLIN_FRAMEWORK_BUILD_TYPE` volvió fresca la **salida de Gradle**. Nada volvió fresco el
**enlace**.

## Por qué, y por qué era invisible

`isStatic = true`, y el framework entra por `OTHER_LDFLAGS = -framework LumeMedLink` más un search
path. La fase `Frameworks` del target está **vacía**. No hay ningún input que Xcode siga y que cambie
cuando cambia Kotlin, así que la tarea de enlace se considera al día.

Un cambio en **Swift** sí fuerza el enlace, y entonces el Kotlin actual entra de arrastre. Por eso
nadie lo vio: sólo muerde cuando el cambio es **sólo Kotlin** — que es la mayoría de los cambios de
este repo, y **todos** los que motivan una medición en iOS.

## El arreglo obvio no funciona, y casi me engaña

Declarar el framework en la fase `Frameworks` como `PBXFileReference` con
`sourceTree = BUILT_PRODUCTS_DIR` es lo limpio, lo declarativo, lo que uno haría. **Medido: no
sirve.** Lo que hay ahí es un **symlink** que crea Gradle, y su propio mtime no se mueve cuando se
reescribe el binario de adentro.

Y casi lo doy por bueno: el primer build después de ese cambio **sí** re-enlazó y el marcador entró.
Re-enlazó porque **había cambiado el archivo del proyecto**. El segundo cambio sólo-Kotlin, con el
proyecto quieto, dejó el binario intacto.

> **La primera medición después de un arreglo está contaminada por el arreglo mismo.** La que vale es
> la segunda. Si hubiera parado en la primera, habría escrito una ADR afirmando un control que no
> hace nada — el género favorito de este repo.

## Lo que sí funciona

La build phase estampa mtime+tamaño del framework en `$DERIVED_FILE_DIR`; si el estampado difiere,
**borra el producto enlazado**. Borrar una salida es lo único que el build system de Xcode entiende
sin discusión, y la fase corre antes de `Sources` y `Frameworks`, así que el borrado ocurre antes del
enlace que lo va a recrear.

Costo de un build sin cambios: **2 s, sin relink, binario intacto**. Medido dos veces.

## Gate para el mecanismo, verificador para el comportamiento

`check-ios-host.sh` afirma que el estampado y el borrado **están escritos** — la llamada, no la
palabra (ADR-0029) — con dos cebos nuevos en `rehearse-gates.sh`, que pasa de 19 a 21.

Ningún grep puede responder «¿volvió a correr el linker?». Eso es
`Scripts/verify-ios-link-freshness.sh`: cuatro builds de Xcode y un **control en vivo que quita la
guarda y exige que el agujero vuelva**. Si el control no reproduce el agujero, el script dice
**INCONCLUSIVE**, no «OK» — un control que no puede fallar no prueba nada, y este repo ya se comió
exactamente ese error en este mismo archivo.

Corrido hoy:

```
── CON la guarda ──        el cambio sólo-Kotlin llegó al binario : PRESENT
── CONTROL, sin guarda ──  el cambio sólo-Kotlin llegó al binario : ABSENT
```

## La lección, que vale más que el arreglo

**Cuando arregles una obsolescencia, pregunta qué eslabón arreglaste y anda a mirar el siguiente.**
F7 arregló la salida de Gradle y nadie preguntó por el enlace. El mismo defecto, dos veces, con tres
semanas de diferencia.

Y la consecuencia incómoda: **toda observación de iOS hecha tras un cambio sólo-Kotlin, en cualquier
máquina, antes de hoy, midió el build anterior.** La ventana de F7 se cerró el 7 de septiembre; ésta
nunca se cerró.

ADR-0030.
