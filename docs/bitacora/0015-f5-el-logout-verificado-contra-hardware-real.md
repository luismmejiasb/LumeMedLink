# 0015 · F5: el logout, verificado contra el almacén real (y no contra un mapa falso)

**Fecha:** 2026-08-21 · **Encargo:** primer slice de Fase B.

## El ataque, en lenguaje humano

Cierras sesión y le pasas el teléfono a alguien. Si «cerrar sesión» no borró el token de verdad, esa
persona entra sin credenciales.

Y «de verdad» carga mucho peso ahí: un logout que limpia la memoria pero deja el archivo cifrado en
disco es recuperable con un volcado forense; y uno que borra los archivos pero **deja viva la
clave** deja cualquier copia suelta todavía descifrable.

## El agujero que este slice cierra

Hasta ahora **todos** los tests de `core/session` corrían contra un **mapa falso en memoria**. El
contrato estaba probado como *lógica* y solamente afirmado como *comportamiento* — la bitácora 0007
lo declaró como pendiente. Un mapa falso no te puede decir si el AndroidKeyStore guardó algo, si el
archivo salió del disco, ni si la clave se borró.

Con la técnica que aprendimos en F4 (tests instrumentados en un Android real), por fin se puede.

## Qué se verificó en el dispositivo — 4 tests, todos verdes

1. **Un secreto sobrevive el viaje de ida y vuelta** por el almacén real respaldado por hardware.
2. **Lo que aterriza en disco NO es el secreto.** El test lee los bytes crudos de los archivos y
   exige que el valor en claro no aparezca. Esto prueba que el cifrado *ocurre*, en vez de confiar
   en que la línea que lo llama existe.
3. **El logout no deja nada legible**: ni en memoria ni en disco, usando el `SessionManager` real
   sobre el almacén real.
4. **El wipe borra los archivos Y la clave del Keystore.** No sólo «ilegible» — *ida*. Borrar la
   clave es lo que convierte «borrado» en «irrecuperable»: si alguna copia del cifrado sobrevive en
   algún lado, sin la clave es basura permanente.

## Un cimiento que se mantiene solo

Los secretos dejaron de ser constantes sueltas y ahora son un **enum** (`SecureStoreKey`), y el test
del wipe **itera sobre el enum**. Consecuencia: un secreto que agregue un slice futuro queda cubierto
por el test de borrado **en el momento en que se declara**. Nadie tiene que acordarse de extender el
test — y acordarse es justo lo que falla.

(Dos tests más caen gratis de ahí: que ninguna clave de almacenamiento esté duplicada —dos secretos
compartiendo clave se pisan y borrar uno borra el otro, un bug que *parece* un logout funcionando— y
que todas lleven sufijo de versión.)

## Lo que el logout NO hace, dicho sin adorno

**Es un logout LOCAL. No revoca nada del lado del servidor.** La app no tiene cableada ninguna
llamada de revocación, así que un refresh token exfiltrado *antes* del logout le seguiría sirviendo
al backend hasta que expire. Dos consecuencias que no son cosméticas:

- **La UI nunca puede decir «cerraste sesión en todos tus dispositivos»** ni nada que insinúe efecto
  remoto. Es la misma disciplina de vocabulario que T16 (una purga local no es el derecho de
  supresión): la app dice lo que hizo, que es «cerrar sesión en este dispositivo».
- **Si el contrato ofrece revocación es una pregunta abierta**, no un supuesto. Se confirma al
  cablear el flujo de auth (F10/F11); si no existe, se convierte en un pedido al backend en vez de
  en un hueco aceptado en silencio.

## Sigue sin verificar

**iOS**, como en todo este repo: el runner de Kotlin/Native no alcanza keychain alguno, así que el
wipe del lado iOS está probado por revisión de código y por los tests de contrato compartidos, no
por un dispositivo. Su prueba llega con el host iOS.
