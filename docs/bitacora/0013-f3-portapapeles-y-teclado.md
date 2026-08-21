# 0013 · F3: el portapapeles y el teclado — y con esto cierra Fase A

**Fecha:** 2026-08-21 · **Encargo:** último slice de Fase A.

## El ataque, en lenguaje humano

Dos maneras de que un dato personal se escape **sin una sola llamada de red**:

**El portapapeles es compartido con TODAS las apps del teléfono.** Copias un RUT para pegarlo en
otro lado, y a partir de ese momento cualquier app instalada puede leerlo. En Android 13+ el
sistema además muestra en un recuadro lo que copiaste, para quien esté mirando la pantalla.

**El teclado ve cada tecla que tocas.** Un teclado de terceros (los que la gente instala por los
emojis o el tema oscuro) puede *aprenderse* lo que escribiste y sugerirlo después **en otra app**.
Ahí está lo contraintuitivo: la fuga no es que lo tecleaste, es que reaparece como sugerencia
cuando estás escribiendo un mensaje a otra persona.

## Qué hice

**1. La app no ofrece copiar.** Ninguna API de portapapeles tiene llamador, y el gate lo rechaza —
incluida `SelectionContainer`, que es la forma silenciosa del mismo problema: hacer un texto
seleccionable le entrega el valor a la barra de copiar del sistema, y de ahí al mismo portapapeles
compartido. Cuando alguna feature necesite copiar de verdad, llega con su propia costura revisada.

**2. Toda entrada sensible pasa por una sola pieza**, `core/input/SensitiveTextField`, y el gate
rompe el build ante un campo de texto crudo en cualquier otro lado. El endurecimiento del teclado es
una lista de atributos chiquitos; una regla por pantalla falla en la única pantalla que se olvida —
y esa es justo la que filtra un RUT al diccionario del teclado.

**3. El endurecimiento distingue el propósito, no apaga todo.** Esto importa: apagar el autofill en
un campo de contraseña **bajaría** la seguridad, porque las contraseñas de un gestor son mejores que
las memorizadas. Entonces: los campos de **credencial** usan teclado de contraseña y enmascarado
(los teclados tienen prohibido aprender de un campo de contraseña) pero siguen aceptando un gestor;
los campos de **dato personal** conservan el teclado que necesitan para ser usables (el teclado
numérico para un teléfono) pero nunca autocorrector ni sugerencias de mayúsculas.

La usabilidad aquí **es** una propiedad de seguridad: un endurecimiento que vuelve los campos
molestos termina siendo rodeado por el siguiente slice.

## Las dos asimetrías, dichas y no maquilladas

- **Portapapeles**: iOS puede marcar un clip como `.localOnly` con expiración; **Android no tiene
  equivalente** — no hay API para sacar un clip de la sincronización entre dispositivos ni para
  expirarlo. Lo único que Android ofrece (`EXTRA_IS_SENSITIVE`, API 33) sólo tapa el recuadro de
  vista previa. Por eso la mitigación honesta en Android es la que tomé: **no ofrecer copiar**.
- **Teclado**: iOS puede rechazar teclados de terceros para toda la app; **Android no puede** — no
  existe API para vetar un IME. La mitigación en Android es por campo y parcial, y decir lo
  contrario sería reclamar una protección inexistente. Y el veto de iOS **tampoco está
  implementado**: necesita el host iOS que no existe.

## Verificación

7 cebos, todos vistos rojos: copiar un RUT (Android), portapapeles de iOS, clipboard de Compose,
texto seleccionable, campo de texto crudo fuera del primitivo, autocorrector reactivado y
sugerencias de capitalización. Más 5 tests sobre las decisiones puras (que ningún propósito permite
autocorrector, que una credencial va siempre al teclado de contraseña aunque el llamador pida otro,
que un dato personal conserva su teclado, y que sólo las credenciales se enmascaran).

**Lo que NO está hecho, listado para que no se confunda con hecho:** el flag
`IME_FLAG_NO_PERSONALIZED_LEARNING` de Android y la exclusión explícita de autofill no son
alcanzables desde el Compose común en la versión pinneada; el veto de teclados de iOS espera el
host. Ninguna de las tres se reclama hoy. Y el comportamiento de un teclado real en un dispositivo
no lo afirma nada de este slice.

## Fase A cerrada

F1 (captura de pantalla, verificado en device), F2 (superficies pre-auth), F3 (este), F4 (biometría
anclada a clave, con 4 tests instrumentados en Android real). Quedan dos colas atadas al host iOS
—el cover de ventana y el veto de teclados— y el end-to-end de F4, que espera el login.
