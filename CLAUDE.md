# Instrucciones — App de gestión y pacientes · LumeMedLink

> Constitución del repositorio, **espejo de la de LumeMed** (`../LumeMed/CLAUDE.md`): misma doctrina,
> plataforma distinta, y las diferencias **declaradas** en vez de asumidas. Ante cualquier duda, esto
> manda sobre el criterio de cualquier herramienta. Cuando esta constitución y la realidad choquen,
> se corrige la constitución primero (con ADR si es estructural) y luego se implementa.
>
> LumeMedLink es la app **phone-first** del ecosistema para lo que rodea a la consulta **sin ser la
> ficha**: perfil, citas, contactos, y a futuro el extremo paciente de la teleconsulta. Existe para
> **separar ambientes**: LumeMed gestiona los datos sensibles del médico; esta app gestiona la
> relación. Esa separación no es organización de código — es la razón de ser del producto, y por eso
> la frontera de datos (§1) es la regla número uno.

---

## 0.0 Apertura de sesión — qué se carga antes de tocar nada

La regla de LumeMed (§0.0 suyo) son tres lecturas: skills, tablero, vault. Aquí se hereda **honesta**:

1. **Skills.** Los nueve de LumeMed son de Swift/SwiftUI y **no aplican** a este repo, con una
   excepción: `lume-security` (hechos verificados de Keychain/LocalAuthentication/CryptoKit) **sí se
   carga cuando la tarea toca el lado iOS** del target KMP. Los equivalentes Kotlin (arquitectura,
   Compose, coroutines, testing) **no existen todavía**: crearlos es deuda declarada (WORKPLAN · Deuda declarada, sin fase dueña todavía), y
   hasta entonces esta constitución es el único criterio cargado. **No se finge una obligación a un
   skill inexistente** — se dice que falta.
2. **El tablero del ecosistema** (§1.1): `../lumemed-cloud-platform/docs/ECOSYSTEM-STATUS.md`, se lee
   al inicio de cada sesión, jamás se copia una cifra suya.
3. **El vault LumeBrain** (§14): la principal fuente de conocimiento de la familia — se LEE al
   abrir y se **ENTRENA con consistencia en toda sesión** (decisión del autor, precisada
   2026-08-20; el «suficiente» está definido en §14). Misma carpeta que toda la familia:
   `~/Documents/LumeBrain/LumeMed/` — el nombre de la carpeta es histórico; guarda el universo Lume
   completo, y este repo es parte de ese universo.

---

## 0. Decisiones tomadas (no se re-deciden)

Cambiar una exige ADR nuevo que derogue al anterior (§10):

| Decisión | Valor | Nota |
| --- | --- | --- |
| Nombre interno | **LumeMedLink** | Decisión del autor (2026-08-17), tomada con las alternativas registradas en la bitácora 0001. Repo/target/carpeta. `git@github.com:luismmejiasb/LumeMedLink.git`. |
| Nombre público (App Store / Play Store) | **PENDIENTE** | Misma pendiente que LumeMed (§0 suyo): el autor evalúa marca pública sin «Lume». Los strings visibles se localizan, así que renombrar público es barato. |
| Plataforma | **Kotlin Multiplatform** — Android + iOS, UI en **Compose Multiplatform**, phone-first | ADR-0002. Portrait primero; iPad/tablet no es objetivo del v1. |
| Dirección del titular | **Dentro de la frontera, geolocalizada por Google vía el backend** | ADR-0027 (2026-08-25, decisión del autor), que enmienda ADR-0001. La llave jamás en el binario: en LumeMed ese fue el motivo de diferir Google (`ADR-0030 de LumeMed`), y acá pesa más porque el teléfono es del paciente y §8.17 lo da por compartido. Pedido `backend-requests/0005`. |
| Frontera de datos | **Cero contenido clínico en esta app.** Ficha, diagnósticos, notas, resultados, recetas: JAMÁS | ADR-0001. Es la decisión que define el producto. La lista de lo que SÍ maneja también es cerrada: perfil, citas (existencia/fecha/lugar), contactos, y las llaves de la teleconsulta futura. |
| Audiencias | **Dos roles**: médico (gestión no clínica) y paciente | El gate de ADR-0006 **se abrió** (ver su enmienda del 2026-09-15): `ADR-0035 del backend` existe y su superficie está construida. Se sigue construyendo primero el lado médico, y la política de sesión del tier de paciente debe aterrizar en un sucesor de ADR-0003 **antes** de que exista código de sesión de paciente. |
| Backend | El mismo **lumemed-cloud-platform**, por su contrato OpenAPI versionado | El contrato de hoy no publica rol paciente ni tier de auth de paciente. Todo endpoint nuevo se pide por `docs/backend-requests/`, como hace LumeMed. |
| Identidad / IdP | **Google Identity Platform** — el mismo IdP de la familia. Médicos: MFA TOTP obligatoria (heredan su cuenta). Pacientes: **política pendiente de ADR** (ADR-0003 la deja abierta a propósito) | Jamás auth casera (espejo del §8.2 de LumeMed). |
| UI | **Compose Multiplatform.** El design system es **`LumeUIComposer`** (`../LumeUIComposer`), el gemelo de LumeUIKit en Compose — **si su Slice 0 sobrevive** (su viabilidad está pendiente del autor, en device). Fallback declarado: un módulo `designkit` interno con los tokens portados, promovible al gemelo después | ADR-0002 §UI. Un componente reutilizable vive en el kit, jamás inline en una pantalla. Cero estilos hardcodeados. |
| Red | **Cliente generado del contrato sobre un stack Ktor endurecido** — espejo de la constitución de LumeNetworking, no de su código | ADR-0004. Ninguna `HttpURLConnection`/OkHttp suelta fuera del stack. |
| Secretos | **Keychain (iOS) / Android Keystore (Android)**, siempre. Jamás `SharedPreferences`/`NSUserDefaults`/código | ADR-0005, espejo de ADR-0005 de LumeMed con las clases por plataforma. |
| Documentos clínicos | **Esta app no muestra, entrega ni transporta documentos con valor legal.** Ni receta, ni certificado, ni licencia — ni en pantalla ni como adjunto | ADR-0007. Es la trampa T1 del backend aplicada a la superficie más tentadora: «mandarle la receta al paciente por la app» reabre la Ley 19.799 entera (ADR-0035 de LumeMed + `ADR-0019 del backend`). |
| Arquitectura | **Por feature** en `commonMain`: `App / Features/<Área>/<Pantalla> / Shared / Core`; dependencias `Features → Core → Shared` | ADR-0008, espejo de ADR-0038 de LumeMed. Los source sets por plataforma (`androidMain`/`iosMain`) son el borde de infraestructura, no una capa. |
| Concurrencia | **Coroutines estructuradas.** Cero `GlobalScope`, dispatchers inyectados, estado de UI en `StateFlow` sobre Main | §6. Espejo de la obsesión de LumeMed §6, en el modelo de Kotlin. |
| Singletons | **Jamás estado global mutable.** `object` de Kotlin sólo como namespace de funciones puras — un `object` con `var` es el singleton prohibido | §3/§6. |
| Postura offline | **Online-only** al arrancar (a lo sumo caché cifrada de lectura) | Espejo del §0 de LumeMed y por la misma razón: la trampa T13 del backend (sin idempotencia, una cola offline fabrica duplicados). |
| Multi-país | El país es dato del tenant; lo chileno en costuras nombradas; teléfonos E.164 | Espejo de ADR-0034 de LumeMed. Aplica igual: esta app pide teléfonos en el perfil. |
| Idiomas | Código/comentarios/commits/ADRs en **inglés**; constitución/WORKPLAN/PROGRESS/bitácora en **español** | §11, convención de la familia. |
| Cómo se mide una ventana de tiempo | **Dos relojes, y cierra el que llegue primero** — el de pared nombra un instante (expiración de token), el de tiempo transcurrido mide una duración (inactividad), y la ventana usa el mayor de los dos transcurridos | ADR-0032 (2026-09-21). El reloj de pared es un *ajuste*: medida sólo con él, la ventana de inactividad se apagaba atrasando la hora del teléfono. |
| Cómo afirma un gate | **El mecanismo, en el lugar donde corre** — la llamada y su dirección, en `src/main`, con comentarios y literales despojados por tokenizador y el XML parseado; y el ensayo con cebo es un artefacto que corre en CI | ADR-0029 (2026-09-21). Nace de dos gates que estuvieron verdes con su control borrado desde que nacieron: F1/F3 satisfechos por un test que no corre, F6/F12 por un comentario XML de cuatro líneas. |
| Estado del ecosistema | **Un solo tablero**, en `../lumemed-cloud-platform/docs/ECOSYSTEM-STATUS.md`. La fila de este repo **ya está ahí** (autorizada por el autor, agregada el 2026-08-20) | §1.1. |

---

## 1. Contexto, objetivo y LA FRONTERA

La familia ya tiene una app que maneja PHI: LumeMed. Esta existe para que **lo demás no viva ahí** —
y para que el paciente tenga por fin una puerta, cuando el backend la construya.

```
   médico ──────────────┐                        ┌───────────── paciente
                        ▼                        ▼
                 ┌────────────┐          ┌──────────────┐
                 │  LumeMed   │          │ LumeMedLink  │   ← este repo
                 │ (la ficha, │          │ (la relación:│
                 │  la PHI)   │          │  perfil,     │
                 └─────┬──────┘          │  citas,      │
                       │                 │  contactos)  │
                       │                 └──────┬───────┘
                       ▼                        ▼
                 lumemed-cloud-platform (un solo backend, un solo contrato)
```

### 1.0 La frontera de datos (la regla que define el producto)

**Qué maneja esta app** (lista cerrada; extenderla exige ADR):

- **Perfil**: nombre, foto, teléfono (E.164), correo, previsión — del médico y, en su fase, del paciente.
- **Dirección** del titular, **geolocalizada** (ADR-0027, que enmienda ADR-0001): entra porque el producto la
  necesita, no porque sea inocua — una dirección **más** una cita revela más que cualquiera de las dos. El motor
  es **Google**, y la app **no habla con Google**: pregunta al backend, que guarda la llave y contrata la
  transferencia. Falla a *tecleado*, nunca a *resuelto* — la capacidad es obligatoria, el resultado no.
  Una coordenada NO es excusa para un mapa de pacientes, una ruta de visita ni un «cerca de ti».
- **Citas**: existencia, fecha, hora, lugar/modalidad, con quién. **Nunca el motivo clínico.**
- **Contactos**: la lista de pacientes del médico como agenda (nombre, teléfono, próxima cita).
- Futuro: la **señalización** de la teleconsulta (unirse a la llamada) — jamás su contenido clínico.
  ⚠️ La trampa T1 del backend nombra a la teleconsulta **por nombre** como detonante: una receta
  emitida en teleconsulta no puede entregarse impresa, así que esa fase **reabre la pregunta de la
  Ley 19.799 para toda la familia** («deferred work with a named trigger»). Se planifica con eso en
  la mesa, no se descubre construyendo.

**Qué NO maneja, jamás, bajo ninguna feature**: ficha, diagnósticos (ni códigos CIE-10), notas,
resultados de exámenes, signos vitales, medicamentos, alergias, documentos clínicos.

**Y la parte honesta:** esta app no es «datos no sensibles». Una cita con un especialista **revela
información de salud** (la agenda de un oncólogo es un dato sensible sobre sus pacientes), y un RUT +
nombre es dato personal pleno bajo la Ley 21.719. La frontera baja el *riesgo* y el *alcance*, no el
estándar: el §8 entero aplica. Lo que la frontera compra es que un dispositivo perdido con esta app
no expone una ficha — expone una agenda, y eso también se protege.

### 1.1 El tablero del ecosistema

Igual que LumeMed §1.1, con las mismas tres reglas: se **lee** al inicio de cada sesión, se **enlaza**
y jamás se copia una cifra, y si el hermano no está en disco se **degrada sin adivinar**. Este repo
además le debía una fila al tablero (lo posee la plataforma): **entregada el 2026-08-20** con
autorización del autor, §3.1 del tablero. *(Corregido 2026-09-21: esta línea seguía pidiéndola un mes
después de entregada, y `PROGRESS` ya la daba por resuelta — el defecto de que nada recomputa la
prosa.)*

### 1.2 Las trampas del backend que muerden A ESTA app

Las diecisiete viven en `../lumemed-cloud-platform/docs/proposals/lumemed-feature-gap.md` §3. Cuatro
son existenciales aquí:

| Lo que parece obvio | Por qué no se puede |
| --- | --- |
| Dejar que el paciente **se cree** una cuenta por su cuenta | **T5 / `ADR-0031 del backend` §4**: el tier de paciente ya existe (`ADR-0035 del backend`, gate de ADR-0006 abierto), pero con **enrolamiento mediado por la clínica**. Lo prohibido nunca fue el paciente: es el portal de autoservicio. Esta app no crea cuentas de paciente. |
| Mostrarle o mandarle la receta / un certificado al paciente | **T1**: entregar un documento con valor legal **sin imprimir** reabre la Ley 19.799 (`ADR-0019 del backend` enmendada, ADR-0035 de LumeMed). Esta app no muestra ni transporta documentos clínicos. Punto (ADR-0007). |
| Un chat médico-paciente que «quede en la historia» | **T4 / `ADR-0007 del backend`**: el chat no es la ficha — jamás se presenta un mensaje como registro clínico. Y la **segunda mitad muerde más** a una app de pacientes: el barrido de retención del backend cubre sólo threads INTERNAL, así que un thread PATIENT hoy **no tiene reloj de retención** (su R-12). Mensajería en esta app tiene esa corrección como **precondición**, no como mejora. |
| Recordarle al paciente «su control de diabetes» en una notificación | **§8.5 de esta constitución**: el payload de una notificación es contenido en pantalla bloqueada. Push sin contenido clínico, siempre — y «diabetes» ES contenido clínico. |
| Dibujar la agenda del día y la lista de pacientes «tal como las devuelve el backend» | **T11**: el interceptor de restricción de tratamiento (Ley 21.719) sólo dispara en rutas que llevan id de paciente; una ruta **tenant-scoped** —la agenda del día, el roster— se le escapa, y un titular con restricción vigente **reaparece** salvo que la query lo excluya y la ruta esté registrada con razón escrita en `UNENFORCEABLE_PATIENT_ROUTES`. Las dos superficies núcleo de esta app (S1.3/S1.4) son exactamente esas rutas: el pedido de contrato de cada una **incluye la pregunta de restricción**, no la deja para después. |
| Reagendar una cita arrastrándola | **T7**: el rol de runtime no puede mover `starts_at`/`ends_at` — reagendar es **cancelar + re-reservar**, dos escrituras con una ventana donde el cupo queda libre, o una operación atómica nueva del servidor que hoy no existe. La pantalla de citas que ofrezca reagendar lo pide como operación del contrato, jamás lo compone con dos llamadas. |

---

## 2. Principios no negociables

1. **La frontera primero.** Toda feature nueva se juzga primero contra §1.0. Si necesita contenido
   clínico, no es de esta app.
2. **Seguridad y privacidad de nacimiento, no al final** — espejo del principio 1 de LumeMed. Cada
   feature nace con su análisis de datos (§8, checklist §12): qué dato personal toca, dónde vive,
   quién lo ve, cuándo se borra.
3. **Encapsulación rígida** (§3): `private`/`internal` por defecto, fronteras por interfaz, inyección
   por constructor. Jamás singletons con estado.
4. **Concurrencia estructurada** (§6): cero `GlobalScope`, cero corrutinas huérfanas, cancelación
   cooperativa.
5. **UI por el design kit** (§5): tokens portados de LumeUIKit, cero literales de estilo en features.
6. **Red por el stack** (§7): cero clientes HTTP sueltos.
7. **Legalista con la 21.719** también aquí: esta app maneja datos personales de titulares chilenos.
   Consentimiento, minimización y derechos se reflejan en código, no en prosa.
8. **Tipado de punta a punta**: modelos del contrato generados; nada de `Map<String, Any>` cruzando
   fronteras.
9. **Sin warnings, CI verde o no se mergea** — `allWarningsAsErrors` en `:composeApp` **y en
   `:androidApp`** (este último desde 2026-09-21: compilaba con warnings tolerados mientras esta
   línea afirmaba lo contrario). La única exención son las compilaciones de **metadata** de Kotlin,
   con su razón escrita en el `build.gradle.kts` raíz. La mitad de «CI verde» sigue siendo **deuda
   declarada**: el workflow está escrito y sólo corrió dos veces, y hoy es `workflow_dispatch`.

---

## 3. Encapsulación

Espejo del §3 de LumeMed, en Kotlin:

- **Acceso mínimo por defecto**: `private` > `internal` > `public`. `public` casi no existe dentro de
  la app; aparece sólo en el borde de un módulo Gradle que lo exija.
- **Fronteras por interfaz**: toda dependencia que cruza capa se expresa como `interface`, inyectada
  por constructor. Un ViewModel conoce el protocolo del repositorio, no el concreto.
- **`object` es un namespace, no un contenedor de estado.** Un `object` con `var`, un
  `companion object` mutable o un top-level `var` son el singleton que el §0 prohíbe. Kotlin hace
  este error idiomático — por eso la regla se escribe, y por eso lleva gate (§9; detekt ya corre,
  pero esta regla exige una custom compilada que no existe: sigue **[manual]**, deuda declarada).
- **Feature-scoping**: los tipos de una feature son `internal` a lo sumo y no se importan desde otra
  área; la comunicación entre áreas pasa por `Core/`.

## 4. Arquitectura — por feature (ADR-0008, espejo de ADR-0038)

```
composeApp/src/commonMain/kotlin/…/
├── app/                    # composición raíz (cablea TODO), el shell
├── features/               # lo que hace el usuario
│   └── <área>/<pantalla>/  #   XScreen · XViewModel · XModels · XUseCases
│       └── shared/         #   lo que usan ≥2 pantallas de ESA área
├── shared/                 # value types puros (data classes, enums, errores)
└── core/                   # capacidades: networking, session, security, designkit…

composeApp/src/androidMain/ | iosMain/   # SOLO adaptadores expect/actual de core
```

- Dirección: `features → core → shared`. `app/` es la única excepción (composición).
- **`expect`/`actual` vive en `core/`, jamás en una feature**: una pantalla no sabe en qué plataforma
  corre; le habla a una interfaz de `core/` cuya `actual` está en el source set de plataforma.
- Regla de promoción, idéntica a LumeMed: lo que un segundo feature necesita **sube a `core/` en el
  mismo cambio**, pierde el nombre de su pantalla de origen, y su test se muda con él.

## 5. UI

- **Tokens, jamás literales** (§5.1 de LumeMed, mismo espíritu): color, tipografía, espaciado y radio
  salen del theme del módulo `designkit`, que **porta los tokens de LumeUIKit a Compose** — misma
  paleta, misma escala tipográfica, mismos roles de botón (filled = primaria, outlineNeutral =
  secundaria, plain = terciaria). El *lenguaje* se comparte; el código no cruza.
- Un componente que usan ≥2 pantallas vive en `designkit`, no copiado.
- **MVVM**: pantalla tonta (renderiza estado, emite intents), ViewModel expone `StateFlow`, los use
  cases hacen el trabajo. Navegación decidida por coordinator, renderizada por el host — espejo de
  ADR-0014 de LumeMed.
- La app traduce; los componentes reciben `String` ya resuelto.

## 6. Concurrencia

- **Coroutines estructuradas**: todo trabajo vive en un scope con dueño (el `viewModelScope` de la
  pantalla, el scope de la sesión). **Cero `GlobalScope`** — es la `Task` huérfana del §6 de LumeMed.
- **Dispatchers inyectados**, jamás `Dispatchers.IO` hardcodeado en un use case: es lo que hace el
  código testeable sin timing.
- Estado de UI: `StateFlow`/`MutableStateFlow` confinado al ViewModel, colectado en Main. Estado
  compartido mutable entre hilos exige una primitiva de `core/` con ADR (espejo del veto a
  locks manuales).
- **Cancelación cooperativa**: cerrar una pantalla cancela sus cargas; un `withContext` tras un
  suspend re-lee el estado (la reentrancia del §6 de LumeMed existe igual en coroutines).

## 7. Red

- **Cliente generado del contrato** de lumemed-cloud-platform (mismo `openapi.json`, generador
  Kotlin pinneado — herramienta exacta se decide al cablear, ADR-0004) sobre un **stack Ktor
  endurecido** que espeja la constitución de LumeNetworking: HTTPS-only fail-closed, TLS ≥ 1.2, sin
  redirects silenciosos, logging **redactado**, retry acotado sólo en GET, mapeo RFC 9457 a un modelo
  de error propio.
- **Cero salida de red fuera del stack**: ni `HttpURLConnection`, ni OkHttp suelto, ni `URLSession`
  en el lado iOS. Imágenes remotas (la foto de perfil): los bytes pasan por el stack, la vista recibe
  el bitmap — espejo del §7 de LumeMed y por la misma razón.
- **Android además lo declara al sistema** (aterrizado en F12/ADR-0016, y hasta entonces esta línea
  afirmaba un control que **no existía**): `networkSecurityConfig` con cleartext negado **y anclas de
  confianza sólo del sistema** — sin CAs del usuario, así que una CA de MDM o de malware no
  intercepta este tráfico. Se declara explícito porque en API 26/27 el default se **invierte** a
  permitido, y porque un default no es una decisión. Más `usesCleartextTraffic="false"` y la
  allowlist de permisos sobre el manifiesto **fusionado** (las dependencias inyectan permisos: hoy
  `INTERNET` lo trae okhttp-android y `USE_FINGERPRINT` androidx.biometric). iOS: ATS sin
  excepciones — **sin verificar, no hay host**.
- Pinning: **diferido, decisión de ESTE repo** (ADR-0004). No se atribuye a LumeMed: su constitución
  lo exige vía el seam de su kit, y lo que existe allá es un candidato de auditoría sin resolver. La
  razón propia: ataca **sólo T5** del modelo de amenaza —el nivel menos probable de este perfil; contra T6 no defiende nada— y convierte cada rotación de
  certificado en un release forzado. Se re-evalúa cuando exista tráfico de producción.

## 8. Seguridad (el espejo de ciberseguridad, por plataforma)

> Espejo del §8 de LumeMed. Donde las plataformas difieren, la diferencia se **declara** — una
> doctrina que finge que Android es iOS protege a medias en los dos.

1. **Datos personales jamás en canales laterales.** Ni en logs (facade redactor, único punto de
   logging), ni en nombres de archivo, ni en analytics. **Default-deny de SDKs de crash/analytics** —
   idéntico al §8.1 de LumeMed y más urgente aquí: con el IdP en Identity Platform, Firebase
   Analytics/Crashlytics está «a una línea» en Android. Desde S0.2 el freno es doble y con gate:
   `check-dependency-allowlist.sh` (denylist nombrado: Firebase, GMS, Sentry, Bugsnag, ACRA) +
   `ForbiddenImport` — ambos vistos rojos con cebo (§9).
2. **AuthN vía Identity Platform.** Médicos: su misma cuenta, MFA TOTP obligatoria. Pacientes: la
   política la fija la ADR del backend que aún no existe (ADR-0006) — esta constitución sólo fija el
   piso: jamás auth casera, tokens de acceso cortos, refresh con rotación.
3. **Pantalla y ciclo de vida, por plataforma:**
   - **Android**: `FLAG_SECURE` en toda ventana con datos personales — en Android los screenshots y
     la miniatura de recientes **sí se pueden bloquear**, y por eso acá es regla dura, no aspiración
     (la diferencia exacta con iOS, donde ADR-0023 de LumeMed retiró el blackout porque no existe API).
   - **iOS**: cover de privacidad al resignar active, en ventana propia — espejo de ADR-0028.
   - **Bloqueo por inactividad** en ambas: ventana deslizante, re-auth biométrica
     (`BiometricPrompt` / `LAContext`) **anclada a material de clave**, no booleana — espejo del
     tier 2 de ADR-0005 de LumeMed. En Android el análogo de `.biometryCurrentSet` es
     `setUserAuthenticationRequired(true)` + `setInvalidatedByBiometricEnrollment(true)`, y es
     **condicional, no exacto**: la invalidación por enrolamiento sólo rige con autenticación
     **por cada uso** y `BIOMETRIC_STRONG`, sin fallback a credencial del dispositivo — las
     condiciones exactas viven en ADR-0005 y son parte del contrato del tier, no un detalle.
4. **Secretos en el almacén de hardware de cada plataforma** (ADR-0005): Keychain
   (`ThisDeviceOnly`, piso passcode-set en device real) / Android Keystore. Jamás
   `SharedPreferences`/`DataStore` planos para un secreto. Nota honesta: Jetpack
   `EncryptedSharedPreferences` está **deprecado** — la implementación Android se decide en el ADR de
   cableado, no se asume esa librería.
5. **Persistencia mínima y sin backup.** Lo poco que se cachea va cifrado y **excluido del backup**:
   Android **`allowBackup=false` Y `dataExtractionRules`, que no son alternativas sino
   complementarios**: el primero corta el backup de nube (un Google One re-viviría la agenda en otro
   equipo), pero a targetSdk ≥ 31 la plataforma **lo ignora deliberadamente para la migración
   device-to-device** (compat change `IGNORE_ALLOW_BACKUP_IN_D2D`) — medido en emulador, no supuesto
   (ADR-0015). En iOS **ya hay un archivo que marcar y está marcado**: el marcador del sentinel de instalación
   (F7, ADR-0028) vive en Application Support **excluido del backup**, que es parte de su diseño — un
   restore tiene que aterrizar sin marcador. *(Corregido 2026-09-21: esta frase decía que no había
   ninguno, escrita antes de F7 y nunca recomputada.)* Fuera de él la app sólo persiste en Keychain, y
   `WhenPasscodeSetThisDeviceOnly` es la única clase que Apple documenta como fuera de todo backup.
   `isExcludedFromBackup` llega cuando exista el primer archivo (F8), no antes — nombrarlo como
   control vigente sería afirmar lo que no está. **Notificaciones push sin contenido**: el payload
   despierta, el contenido se busca autenticado — una cita en pantalla bloqueada es la amenaza 2 del
   threat model.
6. **Transporte sólo HTTPS** (§7).
7. **Ley 21.719 en código.** Régimen general de datos personales + el matiz del §1.0 (metadatos que
   revelan salud). Y la precisión que importa desde el primer slice: **los titulares llegan ANTES que
   la fase paciente** — la agenda y los contactos del médico (S1.3/S1.4) procesan datos de pacientes
   que no son usuarios de la app, y la minimización, la restricción de tratamiento (T11) y el
   vocabulario de borrado aplican ahí desde Fase 1. Una purga local **jamás se presenta como borrado
   legal** (T16): «eliminar de este dispositivo» y «ejercer el derecho de supresión» son frases
   distintas y la UI usa la que corresponde. Derechos del titular como flujo de producto: ADR cuando
   abra la fase paciente. **Nombrada no es implementada** — esta cláusula es aspiracional hasta que
   exista su ADR y su checklist por feature.
8. **Supply chain**: dependencias mínimas, pinneadas por version catalog + lockfile, allowlist con
   denylist nombrado (Firebase Analytics/Crashlytics, Sentry, y toda librería de red/imágenes fuera
   del stack — espejo del gate de LumeMed §9).
9. **Portapapeles**: datos personales (RUT, teléfono) no se copian al portapapeles general sin
   decisión; Android 13+ además muestra el contenido copiado en un overlay del sistema — otra razón
   para no ofrecerlo. Si se copia: iOS con `.localOnly`+expiración; **Android no tiene análogo** —
   no hay API para excluir un clip de la sincronización entre dispositivos ni para expirarlo. Lo que
   hay se usa (`ClipDescription.EXTRA_IS_SENSITIVE`, API 33, que redacta el preview) y **el resto de
   la brecha se declara**: en Android la mitigación real es no ofrecer copiar.
10. **Teclado — la diferencia se declara**: iOS puede rechazar teclados de terceros app-wide (LumeMed
    §8.10) y este repo lo hereda en su lado iOS. **Android no puede**: no existe API para vetar un
    IME. Lo que sí: campos sensibles con `imeOptions` de no-aprendizaje y tipo password donde
    corresponda. La mitigación es menor y se dice — no se finge paridad.
11. **Integridad de runtime**: Play Integrity API (Android) / App Attest (iOS) del lado servidor,
    detección local best-effort — espejo de ADR-0010, con el mismo aviso de honestidad: es defensa en
    profundidad, no garantía. Enforcement sólo en Release.
12. **Deep links**: sólo App Links verificados (Android) / universal links (iOS) — el custom scheme
    está prohibido por la misma razón del ADR-0021 de LumeMed: cualquier app puede reclamarlo.
    Identificadores opacos, un link concede navegación jamás acceso, y con la sesión bloqueada ningún
    link reubica.
13. **Logout = wipe duro**, contrato con test: memoria, disco, Keychain/Keystore.
14. **Sentinel de instalación — asimétrico y declarado**: en iOS el Keychain sobrevive a la
    desinstalación y el espejo de ADR-0037 aplica entero; en Android la desinstalación borra Keystore
    y datos, así que el guardián es innecesario ahí. Es la diferencia de plataforma más limpia de la
    lista: una regla entera que sólo un lado necesita.
15. **Kill-switch / versión mínima**: mismo mecanismo del backend (`GET /v1/app-availability`), misma
    dirección — **falla ABIERTO**; el control autoritativo es el backend rehusando auth.
16. **Canal de eventos de seguridad**: `POST /v1/security-events`, kinds opacos, jamás contenido
    personal. El endpoint existe y **la mitad cliente está construida y probada**
    (`HttpSecurityEventReporter`, F23) — pero **lo cableado en `app/` es el no-op**: no hay base URL
    ni flujo de auth todavía. *(Corregido 2026-09-21: esta línea decía «esta app lo consume», en
    presente, sobre un canal que no escribe nada. Un canal que se cree activo es peor que uno
    ausente, que es lo que ADR-0023 se escribió para evitar.)*
17. **Dispositivo compartido — la amenaza que aquí es MÁS probable que en LumeMed**: el teléfono de
    un paciente lo usa la familia. Sesiones cortas, biometría para reentrar, y ningún dato personal
    en superficies pre-auth (widget, notificación, recientes). El threat model (docs/security/) la
    prioriza explícitamente.

## 9. Testing y gates

- Pirámide espejo: ViewModel (unit, mocks de use cases) ≫ use cases > core > UI/screenshot tests.
- **Datos sintéticos siempre**; un dato real en un test es un incidente.
- Obligatorios por feature que toque datos personales: no-se-loggea test, no-sobrevive-al-logout
  test, authz por rol.
- **Gates** (aterrizados en S0.2, **cada uno visto rojo con archivo-cebo antes de confiar en su
  verde** — bitácora 0003): detekt + ktlint pinneados, daemon JVM 17 pinneado
  (`gradle/gradle-daemon-jvm.properties`); `no_raw_networking` y `secrets_gate` (mitad import) en
  detekt `ForbiddenImport` con `core/` como única excepción; `no_globalscope` doble
  (`GlobalCoroutineUsage` + grep total); en `Scripts/`: `check-feature-isolation.sh` (ADR-0008),
  `check-forbidden-patterns.sh` (`no_document_delivery`, storage plano fuera de `core/`) y
  `check-dependency-allowlist.sh` (lockfiles contra allowlist, denylist nombrado primero). **Siguen
  [manual]**: `no_mutable_object` y `no_hardcoded_style` — exigen regla detekt compilada / design
  system (S0.3); deuda declarada en el yml, no fingida. CI corre gates y compila ambos targets —
  **escrito y jamás corrido: no hay push**. «Una regla sin gate se cae sola» (LumeMed §9) es la
  lección fundante de la familia; fingir gates sería peor que no tenerlos. La lista completa de
  gates vive en `.github/workflows/ci.yml`, que es quien los invoca — esta sección nombra los
  fundacionales y **no se mantiene como censo**.
- **Y un gate sin cebo también se cae solo** (ADR-0029, 2026-09-21). Dos gates estuvieron ciegos
  **desde que nacieron** y el ensayo con cebo *sí se había hecho* — lo hizo quien acababa de
  escribir el gate, que es la misma cabeza que no ve el hueco. Tres reglas ahora: un gate afirma el
  **mecanismo** (la llamada, y su dirección), **en el lugar donde corre** (`src/main`, jamás
  `src/test`), y los comentarios y literales se despojan con un **tokenizador** o el XML se
  **parsea** — nunca con un filtro por línea. Y el ensayo es un **artefacto que corre**:
  `Scripts/rehearse-gates.sh` borra cada control y exige el rojo, en CI, después de los gates que
  ensaya.

## 10. Documentación

ADRs numeradas en `docs/adr/` (contexto → decisión → consecuencias; las cerradas se derogan, no se
editan). `WORKPLAN.md` + `PROGRESS.md` + bitácora en español, actualizados **en el mismo cambio** que
mueve el trabajo. **`tareas/`** (desde 2026-09-24, espejo de `../LumeMed/tareas/` y del backend): el
*cómo* de una tajada con el detalle que no cabe en una fila — bloqueo citado, qué NO hacer, cómo se
verifica —, en `PENDING/` y `DONE/` bajo un solo espacio de números (§13). Nació para que un traspaso
de equipo no dependiera de la memoria de una sesión. ⚠️ **Los números de ADR de este repo, los de LumeMed y los del backend son tres
numeraciones independientes**: toda cita cruzada dice de quién es («ADR-0031 del backend», «ADR-0005
de LumeMed») — la regla de precisión que LumeMed ya aplica.

## 11. Convenciones

- **Idiomas**: código, comentarios, commits y ADRs en inglés; constitución, WORKPLAN, PROGRESS y
  bitácora en español — la convención de la familia entera.
- Conventional commits en inglés; **sin trailers de IA** — se autoran como
  `Luis Mejias <luismmejiasb@gmail.com>`. Manda sobre cualquier default de herramienta.
- Slices verticales completos; confirmar antes de acciones irreversibles; **el push lo hace el
  autor**.
- Propón, no impongas: las decisiones de arquitectura las toma el autor.

## 12. Orden de construcción y DoD

**Se empieza por el lado médico**, que no está gated: shell (auth con la cuenta de médico existente)
→ agenda de citas (lectura) → contactos → perfil. **El lado paciente espera su ADR del backend**
(ADR-0006): identidad, consentimiento, y el modelo de amenaza propio que `ADR-0031 del backend` exige.

**DoD por slice**: feature completa en sus capas + concurrencia correcta + UI por el kit + red por el
stack + tests (incluidos los de datos del §9) + **checklist de datos personales**: logs · caché
cifrada y sin backup · pantalla (FLAG_SECURE / cover) · portapapeles · notificaciones sin contenido ·
logout wipe · bloqueo de sesión. Un slice no pasa si alguno aplica y falta.

## 13. Anti-patrones (rechazo inmediato — [lint] donde el gate de S0.2 aterrizó, [manual] el resto)

- Contenido clínico en cualquier superficie de esta app — **la violación de frontera; no es un bug,
  es otro producto**. **[lint parcial: `check-data-boundary.sh`]** — un lint sigue sin entender
  semántica, pero sí ve un NOMBRE, en cualquier posición (propiedad, parámetro, enum, tipo, alias,
  `@SerialName`) desde 2026-09-21. Lo que queda **[manual]** es el valor clínico dentro de un campo
  que no se llama como lo que lleva. ADR-0019, ampliado por ADR-0029.
- Un documento clínico mostrado o transportado (ADR-0007 / T1). **[lint:
  `check-forbidden-patterns` P2]** — share sheets/exporters; la semántica sigue siendo manual.
- Payload de push con datos personales o clínicos. **[manual]** — no existe push todavía.
- Secreto fuera de Keychain/Keystore; dato personal en disco sin cifrar o dentro del backup.
  **[lint parcial: `ForbiddenImport` + P3 cazan storage plano fuera de `core/`; el resto manual.]**
- `object`/`companion` con estado mutable **[manual]**; `GlobalScope` **[lint: detekt + P1]**;
  dispatcher hardcodeado **[manual]**.
- Cliente HTTP fuera del stack **[lint: `ForbiddenImport` + allowlist]**; DTO tejido a mano contra
  el contrato **[manual]**.
- Estilo hardcodeado; componente duplicado en vez de promovido al designkit. **[manual — espera
  S0.3.]**
- Pantalla con datos personales sin `FLAG_SECURE` (Android) / fuera del cover (iOS). **[manual —
  S1.2.]**
- Un dato real de persona en un test. **[manual]**
- Dependencia nueva sin ADR **[lint: `check-dependency-allowlist`]**; warning tolerado **[lint:
  `allWarningsAsErrors`]**.
- **Un número de documento reclamado por dos archivos** —dos ADR `0029`, dos peticiones `0006`, dos
  bitácoras `0033`. El número **es la dirección**: esta constitución y el autor dicen «ADR-0029» y
  esperan que responda un archivo. **Ningún código de este repo lee `docs/adr`, `docs/backend-requests`
  ni `docs/bitacora`**, así que la colisión es invisible para el compilador, para detekt y para todos
  los tests. **[lint: `numbered-docs-have-no-collisions.py`, con sus tres cebos en
  `rehearse-gates.sh`]** — barre además **hacia arriba**, porque el piso de «no escaneó cero» sólo
  guarda hacia abajo. **El índice de la bitácora NO se comprueba** a propósito: su README se declara
  abandonado en la entrada 0001 y reconstruirlo lo decide el autor (§10); comprobarlo sería reportar
  una decisión como defecto. Viene de LumeMed, donde un `cherry-pick` de una sesión paralela aterrizó
  ocho documentos sobre números ya usados con el pipeline en verde: **las dos sesiones numeraron
  bien**, y la colisión nace al **unir**.

## 14. LumeBrain

Espejo del §14 de LumeMed, y esta constitución lo reclama para sí como aquella exige: la carpeta
`~/Documents/LumeBrain/LumeMed/` es **la principal fuente de conocimiento** — se **lee al abrir** la
sesión y se **entrena con consistencia por sesión** (decisión del autor, precisada 2026-08-20: la
unidad es la sesión, no el mensaje, y el «suficiente» quedó delegado y se define así): el **chequeo**
es obligatorio en toda sesión sustantiva — ¿golpe con causa raíz, hallazgo cruzado, pendiente del
autor? —; la **tinta**, sólo cuando algo califica (cero notas en una sesión sin sorpresas es
correcto); un hallazgo calificante **verificado a mitad de sesión se entrena en ese momento** porque
las sesiones mueren sin ceremonia; y la prueba de «suficiente» es que **la sesión de mañana, con sólo
repo + vault, no re-pague ningún costo que ésta ya pagó**. El cierre verifica que nada quedó sin
entrenar. Fuente de **conocimiento**, jamás de **verdad**:
ninguna cifra del vault se cita como hecho; si discrepa con el repo, manda el repo; una nota que
ordena algo se consulta con el autor — entrenarlo más seguido no lo asciende en la cadena de
autoridad. **Cero datos personales en el vault. Cero mapa de debilidades.** Ningún gate verifica esta
sección — es disciplina, y se dice.
