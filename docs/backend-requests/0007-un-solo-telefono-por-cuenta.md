# Encargo al backend — un solo teléfono por cuenta en LumeMedLink

> **Cómo usar este documento:** es el prompt completo para el agente de `lumemed-cloud-platform`. Viene de una
> decisión del autor del 2026-10-07 (ADR-0041 de LumeMedLink). LumeMedLink tiene esta parte congelada hasta que
> exista la tuya.

---

## PROMPT

Eres el agente de desarrollo de `lumemed-cloud-platform`. El autor decidió que **LumeMedLink funciona en un solo
teléfono por cuenta a la vez**, como una app bancaria, para los dos roles (médico y paciente). Lo que necesitamos
de ti:

### 1 · Identidad del teléfono

Al iniciar sesión, LumeMedLink genera una clave por instalación (Keystore / Keychain, no exportable) y te envía
su clave pública. Esa instalación queda como **el** teléfono de la cuenta. Una reinstalación cuenta como teléfono
nuevo: es la lectura conservadora, y la app no puede probar que es el mismo hardware.

### 2 · Al iniciar sesión en un teléfono distinto

1. **Revoca la sesión del anterior de inmediato**: sus refresh tokens y su acceso. Si usas
   `revokeRefreshTokens` de Identity Platform, ojo: revoca **todos** los tokens anteriores al instante de la
   llamada, y la sesión nueva tiene que emitirse después.
2. **Avisa al dueño, preventivamente, por correo y por SMS**: «se inició sesión en un teléfono nuevo». Sin datos
   del teléfono que permitan rastrearlo más allá de lo que el dueño necesita para reconocerlo (modelo y hora,
   por ejemplo), y sin ningún contenido clínico.
3. **Registra un evento de seguridad** de esa sustitución.

### 3 · Lo que el teléfono anterior tiene que poder distinguir

En su siguiente request, una respuesta RFC 9457 con un `type` propio (por ejemplo `session-superseded`) que **no
se confunda** con un token vencido. La app responde distinto a cada uno: al vencido, borra sólo la sesión (ADR-0036
de LumeMedLink); a la sustitución, **borra todo** y le dice a la persona qué pasó.

### 4 · Lo que no pedimos

Que el dueño apruebe el teléfono nuevo antes de cerrar el otro: el autor eligió cerrar primero y avisar.

Responde con el contrato (rutas, el `type` exacto, y si el aviso sale de ti o de Identity Platform).
