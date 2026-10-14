# Encargo al backend — verificar el teléfono o el correo nuevo antes de aplicarlo

> **Cómo usar este documento:** es el prompt completo para el agente de `lumemed-cloud-platform`. Viene de dos
> decisiones del autor del 2026-10-14: el cambio de teléfono y de correo se verifica con un código antes de hacerse,
> y la app muestra un solo perfil aunque haya uno por clínica. LumeMedLink ya tiene las pantallas, contra su guion.

---

## PROMPT

Eres el agente de desarrollo de `lumemed-cloud-platform`. Hoy `updateMyPatientProfile` aplica un teléfono o un correo
nuevo de inmediato, lo anuncia, y la paciente puede marcarlo «no fui yo» (`disownMyContactChange`). El autor quiere
que el **dato nuevo se verifique antes de aplicarse**: un código al teléfono nuevo por SMS, o al correo nuevo, y el
cambio sólo después de escribirlo bien.

La razón es la toma de cuentas: quien tiene el teléfono desbloqueado un momento puede cambiar el correo a uno suyo y,
con eso, la recuperación de la contraseña. Que el dueño pueda marcar «no fui yo» después ayuda, pero llega tarde.

### Lo que necesitamos

1. **Pedir el cambio**: una operación que recibe el teléfono (E.164) o el correo nuevo y manda un código a ese
   destino. Responde cuándo vence el código y cuándo se puede pedir otro. No aplica nada todavía.
2. **Reenviar el código**, con su límite.
3. **Confirmar**: la operación que recibe el código y recién ahí aplica el cambio. Responde con los intentos que
   quedan si el código es incorrecto, y con un error propio si venció.
4. **Un cambio en todas las clínicas.** La app muestra un solo perfil, y un cambio de contacto debe valer en cada
   clínica donde la paciente tiene perfil, con un solo código. Si prefieres que la app confirme clínica por clínica,
   dilo, pero el código tiene que ser uno.
5. **El aviso a cada clínica y a la paciente**, como hoy (tu `ADR-0057` §5), pero después de confirmar, no antes.
6. **El mismo dato que ya tiene**: un error propio. La app ya lo dice en pantalla.

### Lo que no pedimos

- Verificar la dirección: sigue como hoy.
- Que el código viaje en ningún log o evento.

Responde con el contrato y con cómo queda tu `ADR-0057`.
