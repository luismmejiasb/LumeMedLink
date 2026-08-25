# Encargo al backend — el proxy de geocodificación que las DOS apps necesitan

> **Cómo usar este documento:** es el prompt completo para el agente de `lumemed-cloud-platform`.
> Copialo entero como encargo. Este pedido es distinto de los cuatro anteriores en una cosa: **no lo
> pide sólo LumeMedLink**. `ADR-0030 de LumeMed` ya escribió que éste es «el camino correcto» y lo
> difirió porque el endpoint no existe. Un endpoint desbloquea a las dos apps.

---

## PROMPT

Eres el agente de desarrollo de `lumemed-cloud-platform`. Se te pide **un endpoint de búsqueda de
direcciones que hable con Google por las apps**, de modo que ninguna app lleve una llave de Google
en su binario ni hable con Google desde el dispositivo de una persona.

Trabaja bajo tu propia constitución y tus ADR — donde este encargo y tu constitución choquen, manda
tu constitución y **lo declarás en el output** en vez de resolverlo en silencio.

### 0 · Lo que ya está decidido en las apps (no re-decidir)

- **El motor es Google.** Decisión del autor, tomada dos veces: primero en LumeMed (registrada en
  `ADR-0030 de LumeMed` §Alternativas como «preferencia expresada por el autor: funciona mejor en
  Chile») y reafirmada el 2026-08-25 para LumeMedLink. La razón es calidad de resultados en Chile.
- **La llave jamás va en un binario de cliente.** Ése es el motivo exacto por el que LumeMed difirió
  Google: «una llave dentro de un binario iOS es extraíble — con facturación asociada y con la
  dirección de un paciente al otro lado». En LumeMedLink pesa más todavía: el teléfono es del
  **paciente**, y su modelo de amenaza da por hecho que lo usa su familia.
- **`ADR-0027 de LumeMedLink`** acaba de meter `address` en su frontera de datos con esa condición.

### 1 · Lo que se pide

Una ruta que reciba **un fragmento de texto y nada más** y devuelva candidatos de dirección
resueltos, con coordenadas.

**La firma es parte del pedido, no un detalle.** En las dos apps la costura del cliente recibe un
fragmento y **no tiene parámetro** para nombre, RUT, fecha de nacimiento ni id de paciente:
componer identidad en la consulta es *inexpresable*, no un error que revisar. Se pide que **tu
endpoint tenga la misma forma**, para que esa propiedad no se pierda al cruzar la frontera. Si tu
contrato necesita un identificador por alguna razón, decilo y explicá cuál — no lo agregues en
silencio.

Lo que cada candidato debería traer:

- La **línea a mostrar y archivar**, ya formateada.
- El **contexto** (comuna, región) como texto de apoyo.
- **Latitud y longitud.**
- **El código de comuna, si podés resolverlo.** Éste es el beneficio que sólo existe yendo por el
  servidor: `ADR-0030 de LumeMed` tuvo que dejar `communeCode` en `nil` porque MapKit **nombra**
  comunas y el vocabulario de códigos es tuyo y no existía. Si podés resolver el código desde el
  resultado de Google, las apps dejan de guardar sólo un nombre al lado.

### 2 · Restricciones que vienen de las apps

- **Sólo Chile.** Medido en LumeMed: un sesgo regional **no restringe** resultados — una calle
  chilena matchea calles en Brasil y Argentina, y una de esas puede rankear primero. Archivar una
  coordenada extranjera como domicilio es un error silencioso, permanente y peligroso. Se pide que
  **el servidor descarte lo que no esté verificablemente en Chile**, para que no dependa de que cada
  app se acuerde.
- **No loggees la consulta.** El fragmento es la casa de una persona. Los mensajes de error de los
  proveedores tienden a citar la consulta; las apps ya los descartan de su lado y necesitan lo mismo
  del tuyo.
- **Rate limiting es tuyo.** Las apps ponen debounce (700 ms en LumeMed, como control de privacidad
  y no de rendimiento) y un mínimo de caracteres, pero el freno con dientes es el servidor.
- **Distinguí «preguntamos y no hay» de «no pudimos preguntar».** Las dos apps las muestran con
  frases distintas porque llevan a acciones opuestas. Una lista vacía y un error tienen que ser
  distinguibles en tu respuesta.

### 3 · Lo que este endpoint arregla, y que conviene que sepas

`ADR-0030 de LumeMed` enumeró como pérdidas todo lo que MapKit no puede dar: piso TLS de la app,
pinning, log redactado, attestation, inspección del tráfico. Yendo por vos **no se pierde ninguna**.

Y la fila que le importa más a un abogado que a un ingeniero: hoy Apple se caracteriza como
**destinatario tercero independiente** porque no hay mandato escrito (su hallazgo nº3, registrado
como brecha abierta). Con vos de por medio, Google es **encargado del operador** bajo contrato, y la
transferencia internacional pasa de «una comunicación desde el teléfono de cada persona» a algo
contractualmente controlado. Eso es tuyo para decidir y documentar, no de las apps.

### 4 · Preguntas que las apps NO pueden responder

1. **Base de licitud** para comunicar una dirección a un proveedor de mapas para validarla:
   ¿alcanza la relación de tratamiento, o es uso secundario con consentimiento explícito?
2. **Transferencia internacional** bajo la Ley 21.719: ¿qué mecanismo la legitima?
3. **Aviso al titular**: ¿el aviso de privacidad debe nombrar al proveedor de mapas? Si sí, es copy
   y UI de las apps y necesitamos el texto.
4. **Retención**: ¿guardás la consulta o el resultado en algún lado? Si sí, cuánto y con qué reloj.

### 5 · Formato del output

El esquema en el `openapi.json` (las dos apps generan su cliente del contrato), los códigos de
estado, un ejemplo de request y de respuesta, y **la respuesta explícita a las cuatro preguntas del
punto 4** — aunque la respuesta sea «abierta, es del operador».
