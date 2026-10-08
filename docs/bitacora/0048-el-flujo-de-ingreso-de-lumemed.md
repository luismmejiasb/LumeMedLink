# 0048 — El flujo de ingreso de LumeMed, calcado

**Tipo:** `feature` · 2026-10-07

## Qué llega

El autor pidió calcar el flujo de autenticación de LumeMed —diseño y flujo de pantallas—. Un agente de lectura mapeó el
de LumeMed (pantallas, componentes de LumeUIKit, textos, el `AppRoute` y su política de volver, la taxonomía de errores,
las ADR 0007, 0013, 0014, 0022, 0024 y 0032 de allá) y se reprodujo en `features/auth/` sobre LumeUIComposer. La tabla
pantalla por pantalla y cada desvío con su razón están en ADR-0043.

## Cómo quedó armado

- `core/auth/AuthGateway`: el contrato (RUT y contraseña → qué sigue; código → tokens; vincular; restablecer). Cableado,
  `UnwiredAuthGateway`, que dice «no disponible» en cada paso. Conectarlo es la tarea `0028`, congelada.
- `shared/ChileanRut`: módulo 11, forma canónica, y un `toString` que no imprime el número.
- `features/auth/flow/`: el coordinador (`AuthFlowModel`: camino, dirección, una alerta, el reintento, los tokens entre
  el segundo factor y la biometría), la sesión de un intento (`AuthFlowSession`), el host con la tarjeta focal y las
  transiciones. Se llama `flow` y no `shared` porque el gate de aislamiento trata toda carpeta `shared` como la capa pura.
- Una pantalla por carpeta: `login/`, `mfa/` (el paso de código, que sirve para el TOTP, la confirmación y el código de
  restablecimiento), `totp/`, `biometric/`, `recovery/`, `unlock/`.
- `core/input` suma el RUT (el campo de documento del kit) y `OneTimeCodeField` (el de código del kit).
- El intento vive en el ViewModel del shell: sobrevive a una rotación, sólo en memoria, y se reemplaza por uno vacío al
  reiniciarse o al empezar la sesión.

## Verificación

`AuthFlowTest`, 11 casos en los dos targets: el camino con y sin autenticador, la contraseña que sale de memoria al
usarse, el rechazo en el botón y la falla en la alerta con su reintento, el RUT inválido que no llega al servidor, el
código incorrecto que se limpia, la política de volver, la biometría que falta y reinicia vacío, el restablecimiento,
lo que muestra la pantalla de bloqueo, y que nada tecleado se imprime. Toda la verificación verde (tests, gates, los 86
cebos, los instrumentados).

En el simulador de iOS, con un build temporal que recorre las pantallas: la tarjeta, el ícono, los títulos, el campo de
seis cajas, el disco de volver sólo donde corresponde y la alerta única se ven como en LumeMed. Dos detalles: el enlace
«¿Olvidaste tu contraseña?» quedaba centrado y no al borde (corregido); y los textos de varias líneas no se centran —
`LumeText` no tenía alineación—, pedido al kit, que la agregó el mismo día (`LumeTextAlignment`): los textos de
la tarjeta focal quedan centrados, visto en el simulador.

## Lo que decide el autor

El autofill está excluido de toda la ventana (ADR-0024), así que un gestor de contraseñas no puede llenar el ingreso. El
KDoc de `SensitiveTextField` ya decía que una pantalla de credenciales debería pedirlo de vuelta; queda como pregunta.
