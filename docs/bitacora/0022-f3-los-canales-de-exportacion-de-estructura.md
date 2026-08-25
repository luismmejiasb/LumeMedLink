# 0022 · F3 reabierto: los tres canales por los que el SO copia la pantalla

**Fecha:** 2026-08-25 · **Origen:** cerrar la única cola viva de la lista de fortificación. F6 había
reportado que Compose entrega la estructura de autofill de cada pantalla sin que la app lo pida, y
quedó anotado como **reportado, sin verificar por mí**. Esto es lo que la verificación cambió.

## Lo reportado era cierto, y era impreciso en las dos direcciones

**Más angosto de lo dicho.** `AndroidAutofillManager.populateViewStructure` sólo exporta nodos cuya
semántica cumple `isRelatedToAutofill()`. Un `Text()` de sólo lectura mostrando el nombre de un
paciente **no** se exporta. «La estructura de CADA pantalla» era demasiado.

**Y total dentro de eso.** Todo campo de texto de Compose fija `contentDataType = ContentDataType.Text`
sin condición y sin opt-out (`TextFieldDecoratorModifier.kt:560`, `CoreTextFieldSemanticsModifier.kt:137`).
Así que el conjunto «campos donde esta app recibe datos personales» y el conjunto «nodos entregados
al servicio de autofill del usuario» eran **el mismo conjunto**. Justo la superficie que S1.4 va a
dibujar.

## La trampa: el arreglo obvio no hace nada, y no avisa

`AndroidComposeView` sobreescribe `getImportantForAutofill()` con `return IMPORTANT_FOR_AUTOFILL_YES`
y **sin leer campo de respaldo**. Asignar `composeView.importantForAutofill = …` escribe un valor que
el getter nunca consulta. El setter no falla. En revisión se ve idéntico a un arreglo correcto.

Séptima vez en este proyecto que algo se ve aplicado sin estarlo — y la primera que se caza **antes**
de embarcarlo, no después.

La palanca real es un **ancestro**: `View.isImportantForAutofill()` recorre los padres *antes* de
consultar el valor propio y retorna `false` duro en el primer ancestro marcado
`NO_EXCLUDE_DESCENDANTS`. Leído en la fuente de la plataforma (android-36.1), no recordado.

## El gate se equivocó igual que F20, y su propio cebo lo descubrió

Primer borrador: denylist nombrando el valor malo, aceptando el calificador `View.`.
El cebo escribió `android.view.View.IMPORTANT_FOR_AUTOFILL_YES` y **pasó en verde**.

Mismo error de clase que el prefijo `androidx.` que dejó entrar una API clínica en F20: nombrar lo
malo sólo atrapa las grafías que a uno se le ocurrieron. Se invirtió a allowlist —toda asignación
debe ser `NO_EXCLUDE_DESCENDANTS`, toda llamada debe pasar `false`— y se re-cebó en siete grafías,
incluida un literal numérico crudo. Las siete rojas, las dos legítimas verdes.

## Un control que di por inútil y resultó válido

El test de content capture lo escribí con la advertencia «en un device sin servicio de content
capture esto ya es false, el control no distingue nada». Lo medí igual, y **estaba habilitado antes
de la llamada y deshabilitado después**: la imagen del emulador trae Android System Intelligence como
servicio. El comentario defensivo era falso y se corrigió.

Lección al revés de la habitual: la honestidad preventiva también se verifica. Declarar una debilidad
que no existe ensucia el registro igual que ocultar una que sí.

## Lo que quedó cerrado y lo que no

| Canal | Estado |
| --- | --- |
| **Autofill** | Estaba abierto. Cerrado para toda petición automática, probado en device con cebo (2 tests rojos sin el arreglo). |
| **Content capture** | Ya estaba cerrado — por FLAG_SECURE, no por suerte. Se agrega la bandera propia del app, que es independiente y sobrevive a una pantalla que pierda FLAG_SECURE. **No se presenta como el arreglo.** |
| **Assist** | Vacío por construcción: Compose no implementa `onProvideVirtualStructure`. No hay palanca que tirar; hay un hecho que registrar para que nadie «lo arregle» y crea que ganó algo. |

**Residual declarado:** `AssistStructure.resolveViewAutofillFlags` re-admite las vistas excluidas en
tres casos exactos — petición **manual** del usuario, modo de compatibilidad, y detección PCC. El
modo de compatibilidad es el incómodo: lo habilita la metadata del **servicio** de autofill listando
nuestro paquete, así que no es nuestro para rehusar. Sin verificar en device: probarlo exige escribir
un servicio de autofill, y eso no es este slice.

**iOS sigue intacto en todo esto.** Tiene sus propias superficies de exportación y su propia
respuesta, y ninguna se puede escribir antes de que exista el host.
