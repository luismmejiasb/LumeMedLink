# 0010 · F4: bloqueo por inactividad y biometría anclada a clave

**Fecha:** 2026-08-21 · **Encargo:** cuarto slice de Fase A, el que cierra el corazón de T1/T2.

## El ataque, en lenguaje humano

Dejas el teléfono sobre la mesa, desbloqueado, y te levantas. O lo pierdes con la sesión abierta.
Quien lo agarre entra a tu app sin credenciales — la agenda completa, los contactos, todo. No hace
falta ser hacker: basta esperar a que te distraigas.

Y hay una segunda mitad más sutil, que es la que separa un candado real de uno decorativo. Muchas
apps preguntan al sistema **«¿esta persona se autenticó? sí/no»** y abren si la respuesta es «sí».
Ese candado es un booleano: quien pueda modificar la app (un teléfono con root, una versión
repackageada) lo cambia a «sí» para siempre y el candado deja de existir sin que nadie lo note.

La tercera: si alguien con tu teléfono **enrola su propia huella** en el sistema, un candado
ingenuo lo dejaría entrar con ella — hereda tu sesión.

## Qué hice

**El candado real: material de clave, no un booleano.** Desbloquear ahora significa que el sistema
operativo **entregó un secreto de hardware** que se niega a soltar sin una huella válida en ese
mismo instante.

- **Android**: un par de claves EC **dentro del AndroidKeyStore**. Desbloquear = firmar un reto
  aleatorio con la clave privada a través de `BiometricPrompt`, y verificar esa firma con la clave
  pública. Nadie puede fabricar esa firma sin que el sistema autentique primero. Tres parámetros,
  que ADR-0005 declara **contrato**: autenticación requerida, **por cada uso**, y **la clave se
  destruye si se enrola una biometría nueva** (eso cierra la tercera amenaza). Sólo biometría
  fuerte, sin caída a PIN.
- **iOS**: un ítem de Keychain con control de acceso `.biometryCurrentSet`. **Leerlo ES el prompt**:
  el Secure Enclave evalúa la biometría antes de devolver un solo byte. `.biometryCurrentSet` (y no
  `.biometryAny`) es lo que destruye el secreto cuando cambian las huellas enroladas.
- **La política, en código común y testeada**: cancelar el prompt **no cuesta nada** (poner el
  teléfono boca abajo no es un dedo equivocado — es el error que LumeMed ya pagó en su ADR-0020);
  las huellas equivocadas sí cuentan y a las 5 la sesión termina; enrolamiento cambiado o sin
  biometría disponible **terminan la sesión de inmediato**. Todo hacia el lado cerrado: si el
  candado no sabe si debe abrir, no abre.

## La verificación, que es lo que separa esto de una promesa

**Tres capas, y la tercera es nueva en este repo:**

1. **13 tests de política** en ambos targets: cada rama —cancelar ×20 sin gastar intentos,
   intercalar cancelaciones entre fallos, el contador que se resetea al éxito, enrolamiento
   cambiado, biometría ausente, actividad que no puede desbloquear una ventana vencida.
2. **Gate `check-biometric-contract.sh`**, ensayado con **8 cebos** que son 8 debilitamientos
   reales, todos vistos rojos: quitar la exigencia de autenticación, quitar la invalidación por
   enrolamiento, cambiar biometría fuerte por credencial de dispositivo, cambiar «por uso» por una
   ventana de 300 segundos, meter un fallback DEVICE_CREDENTIAL en otro archivo, degradar iOS a
   `.biometryAny`, quitarle el piso de passcode a iOS, y meter un chequeo booleano
   `evaluatePolicy`. Cada uno compila y sigue mostrando un prompt de huella — por eso el gate
   existe: son las degradaciones que **no se ven**.
3. **Tests instrumentados en un Android de verdad** (`UnlockKeyContractTest`, 4/4 verdes en el
   emulador): le preguntan al sistema operativo, vía `KeyInfo`, si las cuatro propiedades
   **realmente quedaron aplicadas** en la clave que creó. El gate prueba que el código dice las
   palabras correctas; `KeyInfo` prueba que el sistema estuvo de acuerdo. Es la diferencia entre
   una afirmación y un hecho.

**Y el sistema me dio una prueba que no busqué:** la primera corrida falló con
*«At least one biometric must be enrolled to create keys requiring user authentication for every
use»*. O sea: Android **rehúsa crear la clave** si no hay biometría enrolada. Esa excepción es
`GeneralSecurityException`, que es justo la que `enroll()` atrapa para devolver `false` → el shell
lo trata como «no hay tier 2» → la sesión no puede quedarse. El camino fail-closed quedó demostrado
por accidente, con evidencia real. Después enrolé una huella virtual en el emulador (asistente de
Settings + sensor virtual) y los 4 tests pasaron.

## Lo que costó, dicho sin adorno

- **`androidx.biometric` 1.1.0 arrastra 8 dependencias transitivas** al shell, entre ellas
  `appcompat:1.2.0`, de 2020. Es el precio de una matriz de compatibilidad correcta con minSdk 26
  (el `BiometricPrompt` del framework empieza en API 28). Aceptado por ahora y **anotado para F20**.
- **Hueco encontrado en mi propio allowlist**: el prefijo `androidx.` admite **cualquier** grupo
  androidx en silencio, así que estas dependencias nuevas entraron sin que el gate se detuviera a
  preguntar. No es un falso verde (el gate hace lo que dice), es un alcance demasiado ancho.
  Corregirlo es trabajo de **F20**, no de este slice — pero queda declarado, no escondido.
- **Workaround de un bug upstream**: Compose Multiplatform 1.11.1 registra una tarea de copia de
  recursos para la variante `deviceTest` sin configurarle directorio de salida, y eso rompe la
  configuración del grafo. Este módulo **no tiene** recursos Compose, así que la tarea está
  desactivada sólo para esa variante, con su comentario. Se revisa al subir CMP.
- El `MainActivity` pasó de `ComponentActivity` a `FragmentActivity` (lo exige `BiometricPrompt`).
  Re-verifiqué que **FLAG_SECURE sigue en pie** tras el cambio: gate verde y batería completa.

## Lo que NO se puede demostrar todavía

**El candado no es alcanzable corriendo la app**: sin flujo de login no hay sesión, así que la
pantalla `Locked` no se entra y nadie ha visto el prompt de huella en vivo. El mecanismo está
construido, testeado, gateado y su clave verificada contra el sistema operativo — pero el
end-to-end («la app se bloquea a los 5 minutos y pide huella») espera al login. Se dice, no se
insinúa lo contrario.

El lado iOS del gate no tiene equivalente instrumentado: el runner de K/N no alcanza keychain
alguno (bitácora 0007) y no hay host iOS. Espera su slice.
