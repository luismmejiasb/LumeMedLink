# LumeMedLink · Flujo Auth (acceso)

Acceso a la app para pacientes (crear cuenta, ingresar, recuperar) y vinculación de médicos que ya existen en LumeMed.
Las pantallas `*-e*` son **errores y casos borde** del paso que llevan en el nombre (p. ej. `1b-e2` = error de 1b).

---

## 1. Flujo paciente

Crea su cuenta con RUT, verifica su teléfono e ingresa. Tres caminos: **Entrar** (bienvenida → ingresar → ingreso rápido), **Crear cuenta en 3 pasos** (datos → contraseña → verificar teléfono → listo) y **Recuperar contraseña** (RUT → código → nueva contraseña → listo).

- **Entrar** — bienvenida → ingresar → ingreso rápido
- **Crear cuenta · 3 pasos** — datos → contraseña → verificar teléfono → listo
- **Recuperar contraseña** — RUT → código → nueva contraseña → listo

| Imagen | Pantalla | Se llega desde | Qué debe hacer / casos borde |
|---|---|---|---|
| `01-1a-bienvenida.png` | **1a** · Bienvenida | — | Ver imagen. |
| `02-1b-ingresar.png` | **1b** · Ingresar | — | Ver imagen. |
| `03-1c-ingreso-rapido-opcional.png` | **1c** · Ingreso rápido · opcional | — | Ver imagen. |
| `04-2a-crear-cuenta-paso-1.png` | **2a** · Crear cuenta · paso 1 | — | Ver imagen. |
| `05-2b-crear-cuenta-paso-2.png` | **2b** · Crear cuenta · paso 2 | — | Ver imagen. |
| `06-2c-crear-cuenta-paso-3.png` | **2c** · Crear cuenta · paso 3 | — | Ver imagen. |
| `07-2d-cuenta-lista.png` | **2d** · Cuenta lista | — | Ver imagen. |
| `08-3a-recuperar-rut.png` | **3a** · Recuperar · RUT | — | Ver imagen. |
| `09-3b-recuperar-codigo.png` | **3b** · Recuperar · código | — | Ver imagen. |
| `10-3c-recuperar-nueva-contrasena.png` | **3c** · Recuperar · nueva contraseña | — | Ver imagen. |
| `11-3d-contrasena-actualizada.png` | **3d** · Contraseña actualizada | — | Ver imagen. |
| `12-1b-e1-ingresar-datos-incorrectos.png` | **1b-e1** · Ingresar · datos incorrectos | — | Ver imagen. |
| `13-1b-e2-ingresar-cuenta-bloqueada.png` | **1b-e2** · Ingresar · cuenta bloqueada | — | Ver imagen. |
| `14-1b-e3-sin-conexion.png` | **1b-e3** · Sin conexión | — | Ver imagen. |
| `15-1c-e1-ingreso-rapido-no-disponible.png` | **1c-e1** · Ingreso rápido · no disponible | — | Ver imagen. |
| `16-2a-e1-paso-1-datos-invalidos.png` | **2a-e1** · Paso 1 · datos inválidos | — | Ver imagen. |
| `17-2a-e2-paso-1-rut-ya-registrado.png` | **2a-e2** · Paso 1 · RUT ya registrado | — | Ver imagen. |
| `18-2b-e1-paso-2-no-coinciden.png` | **2b-e1** · Paso 2 · no coinciden | — | Ver imagen. |
| `19-2c-e1-paso-3-codigo-incorrecto.png` | **2c-e1** · Paso 3 · código incorrecto | — | Ver imagen. |
| `20-2c-e2-paso-3-codigo-vencido.png` | **2c-e2** · Paso 3 · código vencido | — | Ver imagen. |

---

## 2. Flujo médico

El médico **no se registra aquí**: ya tiene cuenta en LumeMed y solo la vincula, desde «¿Eres médico?» en 1a, con un código o QR → confirmar → listo. Llega a la vista médico (agenda, pacientes, perfil).

- **Vincular** — desde «¿Eres médico?» en 1a · código o QR → confirmar → listo

| Imagen | Pantalla | Se llega desde | Qué debe hacer / casos borde |
|---|---|---|---|
| `21-4a-vincular-con-lumemed.png` | **4a** · Vincular con LumeMed | — | Ver imagen. |
| `22-4b-confirmar-identidad.png` | **4b** · Confirmar identidad | — | Ver imagen. |
| `23-4c-cuenta-de-medico-vinculada.png` | **4c** · Cuenta de médico vinculada | — | Ver imagen. |
| `24-4a-e1-codigo-vencido-o-usado.png` | **4a-e1** · Código vencido o usado | — | Ver imagen. |
| `25-4a-e2-camara-sin-permiso.png` | **4a-e2** · Cámara sin permiso | — | Ver imagen. |
| `26-4b-e1-cuenta-en-verificacion.png` | **4b-e1** · Cuenta en verificación | — | Ver imagen. |

---

## Validaciones del sistema

Aplican a todas las pantallas del flujo. Los mensajes van **literales**.

| Campo o evento | Regla | Mensaje o comportamiento |
|---|---|---|
| RUT | Formato 12.345.678-9 · dígito verificador módulo 11 | «El RUT no es válido. Revisa el dígito verificador.» |
| RUT existente | Único en el sistema | «Ya existe una cuenta con este RUT» → Ingresar o recuperar |
| Teléfono | Celular chileno: +56 9 y 8 dígitos | «Ingresa un celular de 9 dígitos.» |
| Correo | nombre@dominio | «Revisa el formato del correo.» |
| Contraseña | ≥ 8 caracteres · mayúscula · minúscula · número | Reglas en vivo; el botón se habilita al cumplirlas |
| Confirmación | Igual a la contraseña | «Las contraseñas no coinciden.» |
| Consentimiento | Obligatorio · Ley 21.719 | Botón deshabilitado hasta marcarlo |
| Código SMS | 6 dígitos · vence en 10 min · 3 intentos · reenvío cada 60 s | «Código incorrecto. Te quedan N intentos.» / «El código venció.» |
| Ingreso | 5 intentos fallidos | Bloqueo de 15 minutos con cuenta regresiva |
| Recuperar | Respuesta neutra | «Si el RUT tiene una cuenta…» · nunca revela si existe |
| Nueva contraseña | Distinta a la anterior | Cierra las sesiones en otros dispositivos |
| Vinculación | 8 caracteres · uso único · vence en 10 min | «Este código ya no sirve. Genera uno nuevo en LumeMed.» |
| Médico | Prestador verificado en LumeMed | «Tu cuenta aún está en verificación.» |
| Conexión | — | Banner + Reintentar; lo escrito no se pierde |
| Biometría | Disponible y configurada en el teléfono | «Configura Face ID o huella en los ajustes del teléfono.» |
