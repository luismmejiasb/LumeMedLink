# ADR-0027 — La dirección entra en la frontera, y entra geolocalizada por Google vía el backend

- **Status:** Accepted · 2026-08-25 (decisión del autor)
- **Enmienda ADR-0001** (frontera de datos) y por tanto **§1.0 de la constitución**, cuya lista es
  cerrada y cuya extensión el §0 exige que se haga por ADR. No deroga ADR-0001: le agrega un
  elemento y le pone una condición.
- **Related:** `ADR-0035 del backend` (el tier de paciente que dejó al paciente editar dirección);
  ADR-0006 (que mantiene la Fase 2 gated); **ADR-0030 de LumeMed** (el precedente completo, del que
  esto hereda y del que se aparta en dos puntos); §7 (red), §8.8 (dependencias).

## Contexto

La lista cerrada del §1.0 era «nombre, foto, teléfono (E.164), correo, previsión». **La dirección no
estaba.** El choque lo destapó leer `ADR-0035 del backend`, que deja al paciente editar «foto,
correo, teléfono y **dirección**» — y quedó como decisión abierta del autor desde el 2026-08-21,
explícitamente fuera del alcance de cualquier slice de perfil, porque es la regla que define el
producto y no un detalle de UI.

El autor la resolvió el 2026-08-25: **la dirección debe ser editable por el paciente, siempre y
cuando sea geolocalizable, y el motor debe ser la API de Google.**

### Lo que ya estaba decidido en la familia, y que esto no re-decide

`ADR-0030 de LumeMed` es el precedente y hay que leerlo antes que esto. Su hecho central se hereda
entero: **geocodificar es preguntarle a un tercero dónde vive un paciente.** De ahí salen sus
controles, y salvo donde este ADR diga lo contrario, todos aplican acá:

- Minimización **por firma de tipo**: la costura recibe un fragmento y **nada más** — sin parámetro
  para nombre, RUT, fecha de nacimiento ni id de paciente. Componer identidad en la consulta es
  *inexpresable*, no un error que revisar.
- **Una consulta por debounce**, jamás una por tecla. El debounce es un control de privacidad.
- **Cero ubicación del dispositivo.** Sin `CLLocationManager`, sin permiso de ubicación, sin clave
  de plataforma para ello. La región es una constante de Chile.
- **Sólo con tecleo vivo.** No se geocodifica al restaurar un borrador, no hay geocoding inverso de
  coordenadas guardadas, no hay backfill.
- **Nada se loggea** del proveedor: sus mensajes de error tienden a citar la consulta, y la consulta
  es la casa de una persona (§8.1).
- **Se rechaza lo que no esté verificablemente en Chile.** Medido allá: un sesgo regional **no**
  restringe resultados, y archivar una coordenada extranjera como domicilio es un error silencioso
  y permanente.
- **Kill switch con una implementación real en OFF**, no un `nil`.

## Decisión

### 1. `address` entra en la lista cerrada del §1.0 — como dato del titular, no como texto libre

Sigue siendo **dato personal pleno** bajo la Ley 21.719, y el §1.0 ya advierte que esta app no
maneja «datos no sensibles»: una dirección **más** una cita revela más que cualquiera de las dos.
Entra porque el producto la necesita, no porque sea inocua.

**Lo que NO entra con ella:** nada clínico sigue estando fuera, y una coordenada no se convierte en
excusa para un mapa de pacientes, una ruta de visita ni un "cerca de ti". La frontera no se movió;
se le agregó un campo.

### 2. El motor es **Google**, y la app **no habla con Google**

El autor lo decidió y la razón está registrada desde LumeMed: **funciona mejor en Chile**. Este ADR
no la re-discute.

Lo que sí decide es **por dónde**: la app pregunta a `lumemed-cloud-platform` por el stack
endurecido, y **el backend habla con Google, con su llave y bajo su contrato**. Esto es literalmente
lo que `ADR-0030 de LumeMed` llamó «el camino correcto» y difirió por no existir el endpoint. El
pedido de contrato correspondiente es `backend-requests/0005`, y sirve a las dos apps.

**Por qué no la llave en el teléfono**, en las palabras del precedente: una llave dentro de un
binario es **extraíble**, con la facturación colgando de ella y la dirección de un paciente al otro
lado. Y una llamada REST directa **esquiva el §7 entero**.

**Y por qué acá pesa aún más que en LumeMed:** el equipo que teclea cambia de dueño. En LumeMed
teclea el médico en un equipo administrado por la clínica; acá teclea el **paciente en su propio
teléfono** — el mismo que §8.17 nombra como el más probable de estar compartido con su familia. Una
llave de facturación en ese binario está en manos de cualquiera.

### 3. Lo que este arreglo RECUPERA, y es la mitad del argumento

`ADR-0030 de LumeMed` enumeró como **pérdidas** todo lo que un egress de framework del SO no puede
dar. Yendo por el backend, esta app no pierde ninguna:

| Garantía | MapKit en LumeMed | Este arreglo |
| --- | --- | --- |
| Piso TLS gobernado por la app | ❌ | ✅ (§7, HTTPS fail-closed) |
| Pinning posible | ❌ | ✅ (el seam existe; ADR-0017 lo dejó como decisión tuya) |
| Log redactado del §8.1 | ❌ | ✅ (`NetworkLogSink`) |
| Origen pinneado | ❌ | ✅ (ADR-0016) |
| Attestation del lado servidor | ❌ | ✅ (§8.11) |
| Inspección y auditoría del tráfico | ❌ | ✅ |
| Transferencia internacional bajo contrato | ❌ (Apple como **tercero**, sin mandato escrito) | ✅ (Google como **encargado** del operador) |

Esa última fila es la que le importa a un abogado más que a un ingeniero: convierte una comunicación
desde el teléfono de cada persona en una transferencia **contractualmente controlada** por el
operador. Es exactamente la brecha nº3 que `ADR-0030` dejó registrada como hallazgo abierto.

**Y un beneficio que sólo existe yendo por el servidor:** el backend es dueño del vocabulario de
comunas que `ADR-0030` tuvo que dejar en `nil`. Un geocoder del lado servidor **puede** resolver
`communeCode` en vez de guardar sólo el nombre al lado.

### 4. Falla a **tecleado**, nunca a **resuelto** (decisión del autor, paridad con la regla 6 de ADR-0030)

Si el proveedor no resuelve, la dirección se guarda como **texto** y su geocodificación queda vacía.
La UI distingue **«preguntamos y no hay»** de **«no pudimos preguntar»** con dos frases distintas,
porque llevan a acciones opuestas.

La lectura estricta —sólo se guarda lo resuelto— se consideró y **se descartó a propósito**: en
zonas rurales, calles nuevas y direccionamiento informal, un titular que no geocodifica no podría
completar su perfil. Eso no es una validación, es una **exclusión**, y no se toma de pasada.

Así que «siempre y cuando sea geolocalizable» se implementa como **la capacidad es obligatoria, el
resultado no**: la app siempre ofrece resolver, y nunca castiga a quien no resuelve.

### 5. Una sola costura, en `core/`, y ningún SDK de mapas en ninguna plataforma

`com.google.android.gms` está **en la denylist** de `check-dependency-allowlist.sh` con razón
escrita («Play Services arrastra superficie de analytics»). Este ADR **no la deroga y no necesita
derogarla**: yendo por el backend no hay SDK de mapas en el binario, en ninguna de las dos
plataformas. Un ADR futuro que quiera el SDK en el cliente tendrá que derogar esa entrada
explícitamente.

Consecuencia buena y deliberada: **una sola implementación en `commonMain`**, no dos adaptadores con
dos proveedores y dos modelos de amenaza. La asimetría de plataforma que este repo tuvo que declarar
cuatro veces, acá no existe.

## Consecuencias

- **§1.0 de la constitución se actualiza en este mismo cambio.** La regla del preámbulo manda:
  cuando la constitución y la realidad chocan, se corrige la constitución primero.
- **No se construye nada todavía, y eso es una regla y no una excusa.** ADR-0006 punto 1 prohíbe
  cablear el lado paciente **incluso contra mocks** hasta que la superficie exista. El endpoint de
  `backend-requests/0005` tampoco existe. Esta ADR decide; la implementación espera a las dos cosas.
- **Un punto ciego que hoy nadie ve, anotado por adelantado.** `ADR-0030 de LumeMed` descubrió que su
  gate `no_raw_networking` **no veía** un `CLGeocoder` — no hay `URLSession` que grepear. Acá pasa
  igual con `android.location.Geocoder`: es API de plataforma, no una dependencia, así que el gate de
  dependencias es estructuralmente incapaz de verla. El gate que lo cubra se escribe **cuando se
  escriba la costura**, y hasta entonces esto es deuda declarada, no un control vigente.
- **Preguntas legales: se heredan abiertas, no resueltas.** Las seis de `ADR-0030 de LumeMed` siguen
  abiertas. Este arreglo **mejora** la nº2 y la nº3 (transferencia internacional y encargado vs.
  tercero) porque el operador contrata a Google; **no las cierra**, y no le corresponde a un ADR de
  cliente cerrarlas. La nº1 (base de licitud) y la nº4 (aviso al titular nombrando al proveedor)
  quedan como trabajo del operador.
- **Para LumeMed esto no es una corrección, es su continuación.** Su propio ADR dijo que cambiar de
  motor es **un archivo** — `AddressSearching` es la costura, y un `CloudAddressSearch` contra este
  mismo endpoint entra sin tocar nada más. El pedido 0005 desbloquea a las dos apps con un endpoint.
  **Este repo no toca el repo hermano**: registra y propone.
