# 0014 · F4: la invalidación probada en device — y el falso positivo que casi reporto

**Fecha:** 2026-08-21 · **Origen:** el autor preguntó *«¿por qué Fase B y no F4?»*, y tenía razón.

## La corrección de criterio, primero

Yo había cerrado F4 diciendo «falta el end-to-end, espera el login». Estaba **mezclando dos cosas**:

- **(a) que el mecanismo funcione de verdad** — que enrolar una huella nueva destruya la clave. Eso
  **no necesita login**: es un test instrumentado.
- **(b) que la pantalla `Locked` se alcance navegando** — eso sí necesita login.

Sólo (b) estaba bloqueado. Y hay un detalle que lo vuelve fácil: **la propiedad más importante del
tier se verifica sin tocar el prompt**, porque la invalidación revienta al inicializar la firma,
*antes* de que aparezca ninguna UI.

## Lo que se probó

La propiedad que el tier entero paga: **enrolar una biometría nueva destruye la clave**, así que
quien tenga el teléfono no puede agregar su huella y heredar la sesión del médico.

| Paso | Resultado |
| --- | --- |
| Fase A: crear la clave, ¿el sistema la deja firmar? | ✅ sí |
| **Control**: Fase B sin enrolar nada | ❌ falla — «la clave sobrevivió» |
| Enrolar una huella nueva | — |
| Fase B otra vez | ✅ **la misma clave quedó destruida** |

Quedó como procedimiento repetible: `Scripts/verify-tier2-invalidation.sh`, que corre los cuatro
pasos y **exige que el control falle** antes de creerle al paso 4.

## El falso positivo que casi reporto (y por qué el control existe)

El primer intento usó `connectedAndroidDeviceTest` para las dos fases. Fase B «pasó»... en el
sentido de que la clave ya no estaba. Estuve a un paso de escribir «propiedad verificada».

**No era eso.** Corrí el control —fase A, y fase B inmediatamente, sin enrolar nada— y la clave
**también** había desaparecido. La causa: Gradle **reinstala** el APK de test entre corridas, y
Android borra las entradas de Keystore de una app al desinstalarla. Es literalmente la asimetría que
nuestro propio ADR-0005 declara («Android wipes app data and Keystore entries on uninstall»), y me
mordió desde el lado del método, no del producto.

El arreglo: correr la instrumentación ya instalada con `am instrument`, que **no reinstala**. Con
eso el control se comporta como debe (la clave sobrevive y el test falla), y recién entonces el
paso 4 significa algo.

**La lección, que vale más que el slice:** un test de seguridad que pasa por la razón equivocada es
peor que uno que falla, porque cierra la pregunta. El control no es ceremonia — es lo único que
distingue «la clave fue destruida por el enrolamiento» de «la clave nunca estuvo ahí».

## Lo que sigue faltando en F4, ahora sí acotado

Sólo **(b)**: que la app, navegando, llegue a la pantalla `Locked` y muestre el prompt. Necesita
sesión, y la sesión necesita login — que a su vez espera que el backend **construya** el
`ADR-0036` que ya aceptó. Y el lado iOS del gate sigue sin equivalente instrumentado (sin host).

Nada más. El mecanismo está construido, gateado con 8 cebos, verificado por `KeyInfo` en sus cuatro
propiedades, y ahora **probado en comportamiento** con control.
