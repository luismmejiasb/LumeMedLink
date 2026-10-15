# 0050 — La ronda de UI del 2026-10-15 y la sesión de desarrollo con biometría

**Tipo:** `fix` + `feat` + `decisión` · 2026-10-15

Ronda del autor sobre el iPhone del simulador, con el backend local caído buena parte del tiempo. Las decisiones del
autor se tomaron por formulario.

## «Sin internet» no era sin internet

Toda falla de transporte se leía como `Offline`, así que un backend caído (`Connection refused` contra
`localhost:3000`) decía «Sin conexión a internet» con el teléfono en línea. El stack descarta la excepción original a
propósito (lleva la URL), y con ella se iba lo único que distingue los dos casos. Ahora, **antes** de descartarla, se le
pregunta a la plataforma (`meansNoNetwork`: los códigos de NSURLSession en iOS, `UnknownHostException` en Android) y sale
sólo un booleano en `AppError.Retryable(offline)`. Rechazo y timeout son «servicio no disponible». Los tests de «sin red»
lanzaban una excepción genérica; ahora lanzan la de cada plataforma, y hay un test del caso «servidor caído».

La franja negra «No internet connection» sobre el estado de error se quitó (decisión del autor): repetía el estado.

## La API local se caía sola

Nada escuchaba en `:3000`; la base y el correo seguían en Docker. La sesión del backend midió que no la levanta ni la
baja nadie de ese repo, y que su `.env` usa `:8443` con TLS. La hipótesis es `pnpm dev` en modo watch, que reinicia con
cada edición de `apps/api/src`. Queda pendiente un comando persistente para `:3000` sin TLS.

## Lecturas de fondo que tumbaban Inicio, y lecturas en serie que sumaban esperas

- Inicio metía el punto de la campana y la tarjeta de valoraciones en el mismo `allOf` que el perfil y las citas: si
  fallaba `/v1/patient/notices`, todo Inicio pasaba a «Reintentar». Ahora son `SecondaryRead`: un fallo da el valor por
  defecto (sin badge, sin tarjeta) y tras tres fallos seguidos Inicio deja de pedirlas. El error de los avisos vive
  sólo en la pantalla de avisos.
- Con el backend caído, cada lectura tarda unos 3,4 s en fallar por sus reintentos, y Inicio encadenaba cinco o seis:
  el pull to refresh giraba 15–20 s y se leía como colgado. Las lecturas independientes de Inicio, Citas y Mis médicos
  van ahora en paralelo.

## Pull to refresh, campana con número, y lo demás de la ronda

- **Pull to refresh** en Inicio, Citas y Mis médicos con `LumeRefresh` (kit `bd81c3b`), por `PullRefresh` en
  `core/screen`: el contenido queda a la vista y un segundo tirón durante una carga no hace nada.
- **La campana es un conteo** (`unreadNoticeCount`, sobre la primera página del buzón) con `LumeCountBadge` (kit
  `7a0e63b`). Con 0, sin dato o con error no hay nada rojo, y el skeleton tampoco pinta conteo. Medido en el simulador:
  9999 se ve como «99+», completo y sin recorte. El punto se veía «roto» por el recorte circular del blanco táctil, que
  el kit corrigió.
- **Comunas:** el selector es el catálogo territorial completo, precargado al abrir Buscar e independiente de la
  especialidad (decisión del autor). Se fueron la nota y el doble aviso.
- **Mis médicos es de sólo lectura:** sin indicador de detalle, y con «Última visita» con año. El médico ve lo mismo de
  sus pacientes: visitas y última visita, nunca el motivo.
- **El aviso de 24 h** vive en la tarjeta de cada cita que se puede cambiar o cancelar, no al final del scroll.

## La biometría no funcionaba porque ninguna sesión empezaba (ADR-0066)

La maquinaria tier 2 existía y estaba medida, pero `establishSession` —el único lugar que crea la clave— no tenía
llamador: el flujo de acceso es el guion y, por ADR-0044, no abría sesión. Decisión del autor: **un build de debug
contra la plataforma local abre una sesión real** al terminar el ingreso, con la biometría **obligatoria**. El espacio
de los tokens guarda una marca, no una credencial (las llamadas usan el token de desarrollo, en memoria), y la sesión
recuerda su lado (`SESSION_ROLE`). Medido en el simulador: después del tour de ingreso, relanzar la app muestra
«Hola de nuevo» con el desbloqueo biométrico. Lo que no se midió: el toque con la cara, que necesita tocar el
simulador.
