# ADR-0066 — A debug build begins a real session, with required biometrics

- **Status**: Accepted (2026-10-15), the author's decision by form.
- **Amends**: ADR-0044 (the access flow begins no session), for debug builds against the local platform only.
- **Related**: ADR-0037 (a session begins on an empty slate with its tier-2 material), ADR-0011 (tier 2 anchored to
  key material), ADR-0034 (the attempt ceiling), ADR-0014 (the logout contract), ADR-0059 (the patient's session
  policy), ADR-0062 (the local platform), task `0028` (the real sign-in, FREEZE), task `0026` (re-entry without
  biometric hardware, FREEZE).

## Context

The author reported that biometric sign-in did not work. It could not: the tier-2 machinery — the Keystore key behind
`BiometricPrompt`, the Keychain item behind `LAContext`, `SessionLock`, the unlock screen — was built and measured, but
the key is made only in `establishSession`, and nothing called it. The access flow runs against the design's script
and, by ADR-0044, began no session: no token, no key, no lock. The real sign-in waits on Identity Platform and a
deployed platform (task `0028`).

## Decision

1. **A debug build against the local platform begins a real session when the access flow lets somebody in** —
   a first sign-in, a finished sign-up, or «Usar contraseña» from the lock. It goes through `establishSession`
   unchanged: the slate erased, the tier-2 key made, the tokens written, the lock opened.
2. **Biometrics are required.** A phone without them gets no session: the shell says what to turn on, and the person
   goes back to the sign-in with nothing kept. Re-entry without biometric hardware stays task `0026`.
3. **What the tokens slot holds is a mark, not a credential** (`devSessionTokens`). The local platform's requests carry
   the dev token, which never touches the disk (ADR-0062); nothing sends the mark, and it never expires because
   nothing could refresh it. When task `0028` thaws, the backend's pair takes its place and nothing else changes.
4. **The session keeps its side**, patient or doctor (`SecureStoreKey.SESSION_ROLE`), so a relaunched app opens the
   same side once unlocked. A session whose side cannot be read is ended. The logout erases it with everything else.
5. **The consent given in the sign-up crosses the erase.** The slate is cleared of the previous session; that answer
   belongs to this one. The theme and the language go back to the phone's.
6. **With a session, «Cerrar sesión» is the logout contract** (ADR-0014), and the unlocked main app is the `Home`
   destination. The script — every release build until the platform is deployed — keeps ADR-0044: no session.

## Consequences

- On a debug build the whole tier-2 path runs on a phone for the first time: the key at sign-in, the lock after the
  inactivity window or a relaunch, the unlock against the key, the attempt ceiling, «Cambiar de cuenta».
- The simulator needs an enrolled face (Features › Face ID › Enrolled); `Scripts/tour-screens.sh` already enrols one.
- Nothing drawn after the sign-in exists before the session does: while it begins, the shell draws an empty region.
- `DevSessionTest` pins the entry: key, side and open lock; the consent carried and the last session's remains gone;
  no biometrics, no session; a side that cannot be saved leaves no half session.
