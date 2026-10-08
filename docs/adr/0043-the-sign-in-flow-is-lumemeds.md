# ADR-0043 — The sign-in flow is LumeMed's, with its deviations written down

- **Status**: Accepted (2026-10-07) — the author asked for LumeMed's auth flow to be copied, design and screen flow.
- **Related**: LumeMed's ADR-0007, 0013, 0014, 0022, 0024, 0032 (the flow being copied); this repo's ADR-0003,
  0013, 0024, 0033, 0037, 0040, 0041, 0042; task `0028`.

## Decision

`features/auth/` reproduces LumeMed's sign-in, screen by screen, on LumeUIComposer:

| LumeMed | Here |
| --- | --- |
| `LoginFormView` — name over a divider, RUT, password with show/hide, "¿Olvidaste tu contraseña?", "Ingresar" | `login/` |
| `MFAChallengeFormView` — shield, "Verifica tu identidad", six-box code, "Verificar" | `mfa/CodeStepScreen` |
| `TOTPEnrollmentFormView` + confirm — QR, setup key, "Continuar", then the code | `totp/`, `mfa/CodeStepScreen` |
| `FaceIDEnrollmentFormView` — "Activa Face ID" | `biometric/` — where the session begins (ADR-0037) |
| Password recovery → code → new password | `recovery/`, `mfa/CodeStepScreen` |
| `FaceIDLoginFormView` — "Hola de nuevo", "Usar contraseña", "¿No eres tú? Cambiar de cuenta" | `unlock/`, as the lock screen |
| `AuthFlowView` — one focal card, directional transitions, back disc where allowed, one alert | `flow/AuthFlowHost` |
| `AppRoute` + exhaustive back policy | `flow/AuthRoute` |
| Error taxonomy — a refusal flashes on the button, a failure is the flow's alert with retry | `flow/StepOutcome`, `AuthFlowModel` |

The copy is LumeMed's Spanish, except where the platform differs: "Face ID" becomes "biometría".

## Deviations, each with its reason

1. **No "continuar sin activarlo"** on the biometric step: no Lume app runs without biometric re-entry (the
   author, ADR-0037 amended). A phone that cannot gets the alert and starts over.
2. **No "Confiar en este dispositivo"** switch (LumeMed's ADR-0024, thirty days without the second factor): it
   is a session policy this repo has not decided, and a switch the server does not honour would be a control
   that only looks applied.
3. **No "Copiar" on the authenticator's setup key**: §8.9 keeps displayed secrets off the shared clipboard. The
   key can be typed by hand.
4. **No support link**: there is no support channel for this app yet; a link to nowhere is worse than none.
5. **No name in "Hola de nuevo"**: showing it means keeping the person's name on the device, readable on a
   locked screen — the shared-phone threat (§8.17).
6. **The sentence ADR-0041 asks for** is on the biometric step: the phone must be the person's alone.
7. **"Usar contraseña" signs in from the start** instead of LumeMed's password-only re-entry: a new sign-in
   replaces the session (ADR-0037), and there is no partial re-authentication to copy until the backend has one.
8. **The password policy is not checked in the app** — only that both entries match; the policy is the IdP's.

## Not wired, and said so

`core/auth/AuthGateway` is the contract; what is wired is `UnwiredAuthGateway`, which answers "unavailable" to
every step — the backend is not deployed and the author froze what waits on it. The flow runs end to end in
tests against a scripted gateway; on a device, the sign-in shows its real "No pudimos conectar". Wiring the
gateway is task `0028`, in `tareas/FREEZE/`.

## Consequences

- The flow survives a rotation (it lives in the shell's ViewModel, memory only) and is replaced by an empty one
  whenever it restarts or a session begins — LumeMed's wipe-on-leave.
- The kit's fields are reached through `core/input` (ADR-0013): a RUT field and the code field joined
  `SensitiveTextField` and `OneTimeCodeField`.
- Autofill stays excluded window-wide (ADR-0024), so a password manager cannot fill the sign-in today. The
  KDoc of `SensitiveTextField` already says a credential screen should ask for it back; that is a decision
  for the author, not taken here.
