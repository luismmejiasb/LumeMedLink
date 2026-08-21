# 0001 · Nace LumeMedLink: la constitución espejo

**Fecha:** 2026-08-17 · **Encargo del autor:** crear el espejo constitucional y de ciberseguridad de
LumeMed para la nueva app phone-first en Kotlin Multiplatform — pacientes y gestión no clínica del
médico — separando ambientes: LumeMed para los datos sensibles del médico, esta para la relación.

## El nombre

**LumeMedLink**, decisión del autor tras evaluar alternativas (LumeLink, LumeCircle, LumeHub,
LumeGo; se descartó LumeMedLite por la connotación de «versión recortada»). Registrado acá para no
re-litigarlo. El nombre público de tienda queda pendiente, junto al de LumeMed.

## Qué se escribió (y qué NO)

**Documentos, deliberadamente**: la constitución (`CLAUDE.md`), ocho ADRs fundacionales y el modelo
de amenaza. **Cero Kotlin**: el esqueleto del proyecto es S0.1 y llegará con sus gates, porque
escribir código antes que los gates es cómo una constitución se queda en prosa — la lección que la
familia ya pagó.

## Las tres decisiones que definen el espejo

1. **La frontera es el producto** (ADR-0001): cero contenido clínico, lista cerrada de lo que sí, y
   la nota honesta de que una agenda médica revela metadatos de salud — baja el riesgo, no el
   estándar.
2. **El lado paciente está gated** (ADR-0006): `ADR-0031 del backend` exige que la identidad de
   paciente llegue por su propio ADR — tier de auth, consentimiento, threat model. Esta app ES esa
   superficie llegando por la puerta, así que se empieza por el lado médico y el gate se cita, no se
   rodea.
3. **Espejo de doctrina, no de código** (ADR-0002/0004): los kits Swift no cruzan a Kotlin. Cruzan
   sus constituciones — tokens portados a Compose, doctrina de red re-implementada sobre Ktor — y
   cada diferencia de plataforma se declara (FLAG_SECURE existe, el veto de teclados no, el Keystore
   muere con el uninstall).

## La revisión adversarial, antes de darlo por bueno

Tres verificadores independientes leyeron los documentos recién escritos contra sus fuentes (las 17
trampas del backend, los hechos de plataforma Android/iOS, la coherencia interna): **24 hallazgos**,
cada uno re-verificado contra la fuente citada antes de aplicarse. Los que valen contar:

- **T11 era el agujero grande**: la agenda del día y el roster son rutas *tenant-scoped* que escapan
  al interceptor de restricción de tratamiento del backend — un titular con restricción 21.719
  vigente reaparecería en la app cuya bandera es esa ley. La pregunta ahora es parte del pedido de
  contrato de S1.3/S1.4, no un descubrimiento de producción.
- **`setUnlockedDeviceRequired` no es el piso de iOS**: un teléfono sin PIN nunca está «bloqueado»,
  así que la clave sería usable siempre. El piso Android real es el rechazo explícito con
  `isDeviceSecure` al establecer sesión — ADR-0005 corregido.
- **El diferimiento del pinning se atribuía a LumeMed**, que no tiene tal decisión (tiene el mandato
  y un candidato de auditoría sin resolver). Ahora es decisión propia de este repo, con su razón
  propia.
- **Y un hallazgo que era al revés**: dos lentes acusaron la escalera de botones del espejo
  (`outlineNeutral` = secundaria) contra el §5.1 de LumeMed — pero el documento desactualizado era
  **el de LumeMed**: su gate enforza la decisión del 2026-08-10 y su prosa decía la del 07. El espejo
  quedó como estaba y LumeMed corrigió su §5.1 (commit `d0f9539` de aquel repo). Un verificador que
  encuentra la deriva en la dirección opuesta a la que buscaba sigue encontrando la deriva.

## Lo que quedó dicho para el autor

- El **push** de este repo (remote ya configurado) lo hace él, como siempre.
- La **fila en el tablero** del ecosistema espera su autorización.
- El **ADR del tier paciente** es el próximo movimiento de coordinación con el backend cuando decida
  abrir la Fase 2.
