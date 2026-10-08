# 0028 · Conectar el gateway de autenticación

> **Estado:** FREEZE · abierta el 2026-10-07 con el flujo de ingreso copiado de LumeMed (ADR-0043). Espera un backend
> desplegado y la audiencia de LumeMedLink en Identity Platform (`backend-requests/0001`, respondido por la `ADR-0036
> del backend`).

## Qué se construye cuando se descongele

1. Una implementación de `core/auth/AuthGateway` sobre el stack endurecido (§7): cada paso del contrato —RUT y
   contraseña, el código TOTP, la vinculación del autenticador, el restablecimiento— contra los endpoints que el
   backend publique. El mapeo de errores a `AuthFailure` sale del RFC 9457 del contrato, no de adivinar códigos HTTP.
2. Reemplazar `UnwiredAuthGateway` en `app/ShellViewModel` por esa implementación, y el `UnwiredRefreshClient` por el
   cliente de refresh real (ADR-0003).
3. La clave por instalación de un solo teléfono (ADR-0041, tarea `0027`) viaja en el mismo ingreso.

## Cómo se verifica

`AuthFlowTest` ya fija el flujo contra un gateway guionado; la implementación real se prueba con un doble del
contrato detrás del stack **real**, como `HttpSecurityEventReporter`. Y en el emulador, de punta a punta, con una
cuenta sintética del backend de desarrollo.

## Qué NO hacer

- Verificar una contraseña o un código en el teléfono (§8.2).
- Guardar los tokens antes de `establishSession` (ADR-0037).
