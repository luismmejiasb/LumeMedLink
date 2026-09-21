# WORKPLAN — LumeMedLink

> Plan vivo por slices. Se actualiza **en el mismo cambio** que mueve el trabajo (§10). Hermano de
> `PROGRESS.md` (el estado) y de la bitácora (el porqué de cada iteración).

## FASE 0 — Cimientos (sin gate externo)

- **S0.1 · Esqueleto KMP.** Proyecto Compose Multiplatform (androidTarget + iOS), árbol de ADR-0008,
  version catalog con lockfile, CI que compila ambos targets. **DoD**: `./gradlew build` y el target
  iOS compilan en CI; el árbol vacío ya respeta la dirección de dependencias.
- **S0.2 · Gates.** detekt + ktlint pinneados; reglas custom espejo: `no_raw_networking`,
  `secrets_gate`, `no_hardcoded_style`, `no_globalscope`, `no_mutable_object`,
  **`no_document_delivery`** (el gate que ADR-0007 promete — share sheets/exporters sin uso clínico
  legítimo) y el **script de aislamiento por feature** que ADR-0008 exige (espejo del de LumeMed,
  sobre packages); allowlist de dependencias con denylist nombrado (Firebase Analytics/Crashlytics,
  Sentry, loaders de imágenes con red propia). **Cada gate se ensaya con archivo-cebo antes de confiar en su verde** — la
  lección fundante de la familia. Al aterrizar, las etiquetas [manual] del §13 migran a [lint].
- **S0.2b · Espejo de red** *(insertado por el handoff del 2026-08-20, encargo del autor)*: la
  doctrina de LumeNetworking sobre Ktor en `core/networking` (ADR-0004), con seams de sesión y de
  log, engines por plataforma y tests de contrato en `commonTest` corriendo en ambos targets.
  **Hecho** — bitácora 0004; el refresh single-flight y el facade real de logs llegan con S1.1.
- **S0.3 · Design system.** Depende de la decisión pendiente del autor sobre **LumeUIComposer** (el
  gemelo de LumeUIKit en Compose, `../LumeUIComposer`, hoy esqueleto con su Slice 0 sin juzgar):
  - Si el gemelo sigue → este repo lo consume por path local (como LumeMed consume LumeUIKit) y
    este slice se reduce a cablear el theme.
  - Si se archiva → módulo `designkit` interno con los tokens portados de LumeUIKit, verificados
    contra el catálogo `LumeUIExample` a ojo (ADR-0002).
  **Este slice no se empieza antes de esa decisión** — construir el fallback con el gemelo vivo es
  duplicar; construir contra el gemelo antes de su veredicto es apostar el shell a un spike.

## FASE 1 — Lado médico (no gated)

- **S1.1 · Shell + auth.** Login con la cuenta de médico existente (Identity Platform vía backend;
  scope estrecho — el pedido de contrato es parte del slice, ADR-0003). Sesión: tokens en su tier,
  bloqueo por inactividad, biometría anclada a clave (ADR-0005), logout wipe con test.
  **En curso (2026-08-21):** el pedido de contrato está escrito
  (`docs/backend-requests/0001-token-de-alcance-estrecho.md`) y espera el ADR del backend; mientras
  tanto avanza la mitad sin UI (`core/session`). La UI del login es lo último — espera S0.3 o nace
  con placeholder sin estilo.
- **S1.2 · Checklist de plataforma del §8.** FLAG_SECURE + cover iOS, `allowBackup=false` +
  `dataExtractionRules`, networkSecurityConfig, ATS. Verificado en device, no sólo declarado.
- **S1.3 · Agenda (lectura).** Las citas del médico. Sin motivo clínico en ningún DTO (ADR-0001 se
  verifica en el pedido de contrato, no después). **El pedido incluye la pregunta T11**: la agenda es
  una ruta tenant-scoped que escapa al interceptor de restricción de la 21.719 — quién excluye al
  titular restringido y con qué registro (`UNENFORCEABLE_PATIENT_ROUTES`) se decide en el contrato,
  no se descubre en producción. Reagendar, si entra al slice, es **una operación atómica del
  servidor** (T7: cancel+rebook deja el cupo libre en la ventana) — y si el contrato no la tiene, la
  pantalla no la ofrece.
- **S1.4 · Contactos.** La lista de pacientes como agenda: nombre, teléfono, próxima cita. Misma
  pregunta T11 que la agenda (es la otra ruta tenant-scoped), y el vocabulario T16 desde el diseño:
  quitar un contacto de la vista **no es** el derecho de supresión, y la UI no puede insinuarlo.
- **S1.5 · Perfil.** Ver/editar del médico; foto por el stack (bytes → bitmap, ADR-0004). Si la foto
  viaja por la infraestructura de archivos de la plataforma, **«subido» no es «visible»** (T10: nace
  PENDING y sólo un veredicto CLEAN del antivirus lo vuelve AVAILABLE; con `ANTIVIRUS_MODE=disabled`
  en dev nada se vuelve disponible jamás) — la pantalla se diseña sobre disponibilidad eventual, y el
  entorno dev necesita el modo del backend que sí resuelve.

## FASE 2 — Lado paciente (gate ABIERTO desde 2026-08-27)

- **S2.0 · El pedido al backend. HECHO.** `docs/backend-requests/0001` pedía el ADR del tier paciente
  que `ADR-0031 del backend` exige. El backend lo aceptó (`ADR-0035 del backend`) y después publicó
  la superficie, en su encargo saliente `docs/backend-responses/0003-patient-tier-ready.md`, que
  trae una sección dirigida al agente de este repo. ADR-0006 quedó **superada**; lee su enmienda
  antes de planificar esta fase.
- **S2.1 · La política de sesión del tier, primero.** Sucesora de ADR-0003, escrita **antes** de que
  exista código de sesión de paciente: MFA (el backend decidió TOTP obligatoria también para
  pacientes), recuperación y enrolamiento mediado por la clínica. Es el punto 3 de ADR-0006, lo
  único que sigue vigente de ella, y no se descubre dentro de una slice de feature.
- S2.2+ · Perfil del paciente y sus citas, contra el contrato ya publicado.
  - **Reservar trae un 409 que sólo este cliente puede explicar** *(recibido del backend el
    2026-09-21, contrato 0.50.0, su ADR-0051)*. Una paciente puede reservar con varios médicos pero
    **nunca dos a la misma hora**, y el servidor lo rechaza **sin poder decir dónde está el choque**:
    el aislamiento por tenant impide que la transacción lea filas de otra clínica. `listMyAppointments`
    sí devuelve su agenda cruzada, así que **el teléfono puede nombrar lo que el servidor no** — es
    su propio dato, mostrado a ella.
    - `patient_unavailable` ≠ `conflict`. El primero es «ella está ocupada, sólo otra hora sirve»;
      el segundo es «ese cupo está tomado, prueba otro cupo o profesional».
    - Se distinguen por el **`type`** (`/problems/patient_unavailable`), que es el único campo que
      nuestro stack conserva — `title` y `detail` se descartan (ADR-0004). El cuerpo no trae la otra
      cita **a propósito**, y está bien así.
    - **Jamás reintentar automáticamente**: este 409 no es transitorio. El mismo par
      paciente+hora no puede tener éxito por esperar. Encaja con T13 y con que el stack no reintente POST.
    - Una solicitud **pendiente** (`REQUESTED`) reserva su hora igual que una confirmada. Los bordes
      que se tocan (10–11 y 11–12) **no** son choque.
    - **El predicado del choque es una COPIA de una regla que la base de datos posee**: fila cuyo
      `status` no sea `CANCELLED` y cuyo rango se solape. `listMyAppointments` devuelve también las
      canceladas (visibilidad más ancha que el enforcement, que es la dirección segura) — así que
      filtrar por estado es nuestro, y si no lo hacemos vamos a nombrar con total confianza una cita
      cancelada como la razón. **Queda escrito que es una copia**: quien toque cualquiera de las dos
      tiene que saber que la otra existe. Es el defecto más repetido de este ecosistema y no avisa.
    - **`bookMyAppointment` NO TIENE llave de idempotencia — y el tier de al lado sí**
      *(recibido 2026-09-21)*. `book` y `transition` del tier clínica llevan `clientId` con único
      parcial detrás; el cuerpo del de paciente es `professionalId`, `locationId`, `startsAt`,
      `endsAt` y nada más. Consecuencia concreta y fea: si una reserva **tiene éxito y se pierde la
      respuesta**, un reintento manual del mismo cupo no duplica —la restricción del profesional lo
      impide— pero vuelve como **409 `conflict`**: se le dice que falló justo cuando sí tiene la
      hora. Seguro, y equivocado en pantalla. **Mitigación nuestra: releer `listMyAppointments`
      antes de mostrar CUALQUIER fallo de reserva.** La regla de no reintentar solo evita la versión
      automática; ésta es la manual.
    - **El caso sin salida, que el backend fijó con test en vez de arreglar** (y explica por qué en su
      ADR-0051): con un vínculo **REVOCADO**, la hora sigue comprometida pero la cita **desaparece de
      su agenda**. Recibe `patient_unavailable` y el teléfono no encuentra nada que nombrar. **El texto
      de respaldo no es un adorno: es el que sostiene ese caso** — «ya tienes otra hora a esa misma
      hora» sin prometer decir cuál. Las dos alternativas eran peores: liberar la hora que sigue
      comprometida, o mostrarle la agenda de una clínica que le revocó el acceso.

## FASE 3 — Horizonte

- Teleconsulta (extremo paciente): señalización sí, contenido clínico jamás (ADR-0001). El extremo
  médico vive en LumeMed; los dos slices se coordinan por el tablero para no construirla dos veces.

## Deuda declarada

- Skills Kotlin equivalentes a los nueve de la familia Swift: no existen (§0.0).
- ~~Fila de LumeMedLink en el tablero del ecosistema~~ — **entregada el 2026-08-20** (§1.1). Se deja tachada, no borrada: siguió pidiéndose un mes porque nada recomputa la prosa.
- Nombre público (App Store / Play Store): pendiente, junto al de LumeMed (§0).
