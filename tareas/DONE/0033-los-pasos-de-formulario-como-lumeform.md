# 0033 · Los pasos de formulario como `LumeForm`

> **Estado:** DONE (2026-10-14) · ADR-0054 · Todas las pantallas con campos son `LumeForm`, y `check-form-steps.sh`
> rechaza un campo dentro de `LumeContainer`. La revisión en iOS pasó a la tarea 0035.

## De dónde sale

El autor vio en el paso 2b («Crea tu contraseña») varios problemas:

- el checklist lejos de su campo;
- los consentimientos sueltos y con demasiado margen, cuando son parte de «Continuar»;
- un teclado que no lleva de un campo al siguiente hasta liberar el pie.

Pidió que el arreglo sea una regla de todos los formularios, y la eligió por formulario: `LumeForm` en el kit, más la
regla en la constitución (§5).

## Qué se hace

1. Cuando el kit entregue `LumeForm`, migrar a él, en este orden:
   - 2b (contraseña): el checklist pegado al campo, y los tres consentimientos en el pie junto a «Continuar»;
   - 2a (datos);
   - 2c (código);
   - recuperación e ingreso;
   - todo otro formulario de `features/`.
2. **La cadena del teclado, por pantalla:**
   - en 2b, Contraseña → *Siguiente* → Repetir → *Listo*, que cierra el teclado;
   - en 2a, RUT → nombres → celular → correo → *Listo*.
3. Un gate que rechace `LumeContainer` con un campo dentro en `features/`, con su cebo en `rehearse-gates.sh`.
   Mientras no exista, la regla de §5 es [manual].
4. Verificación en el emulador y en el simulador, con el teclado real: un defecto de teclado no se ve en un test
   (golpe 29 del vault).

## Qué NO hacer

- No armar el pie fijo dentro de la app con `Box` o `imePadding` propios: es UI propia, y se pide al kit.
- No poner los consentimientos en el `action` de `LumeContainer`: ese slot sube con el teclado.

## Hecho antes de la migración

- 2026-10-13: los tres consentimientos de 2b quedaron agrupados con espaciado chico. **Casi no cambió nada en el
  emulador**: el hueco parece venir de `LumeConsentRow`, que reserva una línea para «Obligatorio». Se agregó al pedido
  del `LumeForm` en la sesión del kit.

## Avance 2026-10-13

- **Migradas: 2a, 2b y 3c**, todas con `LumeForm`.
  - El checklist de la contraseña va en el `below` del campo (`NewPasswordFields`), y los consentimientos de 2b en el
    `footer`.
  - Visto en el emulador Android con el teclado en pantalla:
    - la cadena *Siguiente* recorre los siete campos de 2a y lleva de Contraseña a Repetir en 2b;
    - en el último campo la tecla ✓ cierra el teclado;
    - el teclado tapa el pie y «Continuar», y sólo se mueven los campos;
    - con los dos consentimientos obligatorios, «Continuar» se habilita.
  - Falta verlo en iOS, donde la tecla debería decir «Continuar».
- **Pendientes, cada una con su razón:**
  - **1b (inicio de sesión):** el botón principal está dentro del contenido, y lo fijo abajo es el enlace
    «¿No tienes cuenta? Créala». Pasarla a `LumeForm` cambia el diseño: el botón baja al pie y el enlace pasa a
    `footer`, sobre el botón. Eso lo decide el autor.
  - **2c, 3b y el código del médico:** van centradas, y `LumeForm` alinea arriba. Además, `LumeOTPField` queda fuera de
    la cadena. Hay que decidir si estas pantallas se quedan en `LumeContainer` o el kit agrega el centrado.
  - **3a (RUT de recuperación) y el código ya usado del médico:** centradas, como las anteriores.
- **El hueco entre consentimientos** es el blanco táctil de 48 dp (§0.3 del kit). La sesión del kit se lo consulta al
  autor.
- **Decisiones del autor (2026-10-13, por formulario), pedidas a la sesión del kit:**
  - `LumeForm` gana un `alignment` centrado, y 2c, 3b, 3a y el código del médico se migran con su diseño intacto, sin
    excepción a la regla;
  - en 1b, «Ingresar» pasa a la acción fija y el enlace va debajo del botón, en un slot nuevo bajo la acción.
  - Se migran cuando el kit los entregue.

## Cierre de la migración (2026-10-13, tarde)

- El kit entregó en `7b7c1eb`:
  - ningún campo reserva la línea de mensaje;
  - 12 dp entre campos;
  - `belowAction`.
  - También trajo un `alignment` centrado, pero **no hacía falta**: las pantallas «centradas» van arriba, y lo centrado
    es su `LumeStack` en horizontal. Siguen así dentro de un `LumeForm` alineado arriba. El error de descripción fue de
    esta sesión, y se le avisó al kit.
- **1b:** «Ingresar» quedó en la acción fija, con el enlace en `belowAction`.
- **3a:** «Enviar código», que iba debajo del campo, pasó a la acción fija. Es un cambio de diseño que pide la regla.
- **2c, 3b y el código del médico (también el ya usado):** pasaron a `LumeForm` con su `LumeStack`.
- Vistas en el emulador: 1b, 2a y 3a, sin caídas. **Falta:** el gate del punto 3 y la verificación en iOS.

## Cierre 2026-10-14

- **El gate del punto 3:** `Scripts/check-form-steps.sh`, en CI.
  - Rechaza un campo de `core/input` dentro de `LumeContainer` o de un envoltorio suyo, como `PushedScreen`.
  - Ve el campo aunque llegue por un helper, como `NewPasswordFields`.
  - Los comentarios y los strings se despojan con el tokenizador (ADR-0029). Hay dos cebos en `rehearse-gates.sh`, los
    dos rojos.
- **Lo que deja fuera, a propósito:**
  - `SensitiveSearchField`: un buscador filtra una lista y no envía nada, así que no es un paso de formulario.
  - Los sheets, que no son `LumeContainer`: sus campos se revisan a ojo.
- **No se vio en iOS:** la cadena del teclado, la tecla «Continuar» y el pie bajo el teclado. Esa revisión pasó a la
  tarea 0035.
