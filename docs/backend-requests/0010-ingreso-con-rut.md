# Encargo al backend — el paciente ingresa con RUT y contraseña

> **Cómo usar este documento:** es el prompt completo para el agente de `lumemed-cloud-platform`. Viene de una
> decisión del autor del 2026-10-14 (ADR-0058 de LumeMedLink), tomada después de leer los riesgos de usar el RUT como
> identificador. LumeMedLink tiene el ingreso real congelado hasta que exista lo tuyo (su tarea `0028`).

---

## PROMPT

Eres el agente de desarrollo de `lumemed-cloud-platform`. El autor decidió que **un paciente de LumeMedLink ingresa
con su RUT y su contraseña**, y después con su código TOTP, como en los bancos chilenos. Tu `ADR-0035` ya fija el
TOTP obligatorio y el enrolamiento con el código de la clínica, y eso no cambia. Lo que cambia es el identificador:
hoy Identity Platform autentica por correo o teléfono, y el RUT nunca es un identificador expuesto.

### Lo que necesitamos

1. **Una forma de autenticar con RUT y contraseña** contra la cuenta del IdP. La forma la eliges tú: una operación
   previa que traduce el RUT a la cuenta, un token propio del IdP (custom token) emitido tras verificar, u otra que
   respete tu constitución. La app manda el RUT **sólo en el cuerpo**, nunca en la URL.
2. **Tres condiciones que son parte del pedido, no opciones:**
   - **la misma respuesta y el mismo tiempo, exista o no una cuenta con ese RUT.** Una respuesta distinta revela que
     una persona es paciente en una clínica: en salud, eso es un dato sensible (Ley 21.719);
   - **los intentos se frenan, no se bloquean en duro.** Conocer un RUT no puede bastar para bloquearle la cuenta a su
     dueño;
   - **un RUT por cuenta, fijado al enrolar.** Tu respuesta `0050` dice que cada clínica registra su propio RUT y que
     pueden diferir. El RUT de ingreso es el de la cuenta, no el de una clínica, y una corrección de una clínica no
     lo cambia.
3. **Al enrolar** (`redeemPatientEnrolment`), la cuenta queda con su RUT de ingreso y su contraseña, y el TOTP se
   empareja ahí mismo. Dinos qué datos pide esa operación y quién los trae: la clínica en el código o la paciente al
   canjearlo.
4. **La recuperación de la contraseña**, con el mismo cuidado: la misma respuesta exista o no la cuenta, y el código
   al canal que la cuenta ya tiene, nunca a uno que se escriba en ese momento.
5. **En el token de desarrollo**: que `/dev/token` pueda emitir el de `dev-patient` a partir de su RUT sembrado, para
   que la app pruebe el camino entero en local.

### Lo que no pedimos

- Registro propio del paciente: sigue prohibido (T5, tu `ADR-0031` §4).
- Que el RUT viaje en ningún log, URL o evento.

Responde con el contrato (operaciones, cuerpos, el `type` de cada error) y con lo que decidas sobre el punto 3.
