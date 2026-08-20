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

## FASE 2 — Lado paciente (GATED: ADR-0006)

- **S2.0 · El pedido al backend.** `docs/backend-requests/0001`: el ADR del tier paciente que
  `ADR-0031 del backend` exige — identidad + proofing, consentimiento, authz fuera de `Membership`,
  threat model propio. **Nada de esta fase se construye —ni contra mocks— antes de que ese ADR
  exista y esté aceptado.**
- S2.1+ · Se planifican cuando el gate abra: perfil del paciente, sus citas, y la política de sesión
  del tier (sucesora de ADR-0003).

## FASE 3 — Horizonte

- Teleconsulta (extremo paciente): señalización sí, contenido clínico jamás (ADR-0001). El extremo
  médico vive en LumeMed; los dos slices se coordinan por el tablero para no construirla dos veces.

## Deuda declarada

- Skills Kotlin equivalentes a los nueve de la familia Swift: no existen (§0.0).
- Fila de LumeMedLink en el tablero del ecosistema: pendiente de autorización del autor (§1.1).
- Nombre público (App Store / Play Store): pendiente, junto al de LumeMed (§0).
