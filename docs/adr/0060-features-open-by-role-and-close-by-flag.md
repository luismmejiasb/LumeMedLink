# ADR-0060 — A feature opens by role and closes by flag: `FeatureGate`

- **Status**: Accepted (2026-10-14), the author's decision: «OK con nuestra arquitectura FeatureGate, sólo asegúrate de
  que sea robusta, testeable y sólida». The architecture is this repo's own; nothing of it comes from another
  employer's code.
- **Related**: ADR-0052 (Remote Config is allowed), ADR-0056 and ADR-0058 (one door, the role from `/v1/me`), §3
  (encapsulation, interfaces by constructor), §6 (structured concurrency, injected dispatchers).

## Context

The author wants every main feature of the app behind a flag, served by Remote Config, and the doctor's features
unlocked when the server says the account is a doctor's. Two different questions hide in that sentence:

- **May this person use the feature?** That is the role, a fact about one account. Only the server knows it.
- **Is the feature switched on right now?** That is a flag, the same for everyone (or for a segment). Remote Config
  serves it, and anyone who controls the device can tamper with what it serves.

Mixing them would let a flag grant a role, which is an authorization decision taken by the least trustworthy source
in the app.

## Decision

1. **A closed catalog.** `Feature` is an enum in `core/features`. Each case has one rule, written in one exhaustive
   `when`, so a new case does not compile until its rule exists:
   - the **roles** that may use it (`PATIENT`, `DOCTOR`);
   - the **Remote Config flag** that switches it, with its **default compiled into the binary**.
2. **One pure decision**, the only place where the answer is computed:

   ```kotlin
   available = role is known && role in rule.roles && flag(rule.flag)
   ```

   - **The role can only open; the flag can only close.** A flag never grants a role.
   - **Unknown role → closed.** Before `/v1/me` answers, or when it fails, no role-gated feature is shown.
   - **Unknown flag → the compiled default.** Remote Config not answering never empties the app, and never opens
     a feature whose default is off.
3. **Two ports, by constructor, each one narrow** (interface segregation):
   - `RoleSource` publishes the role as a `StateFlow` (`Unknown` or `Known(role)`). It is fed by `/v1/me` (backend
     request `0011`). Until that exists, it is fed by the access gateway's `AccountKind`.
   - `FlagSource` publishes the flags' values. It is implemented over Remote Config, which today lives inside the
     wide `Telemetry` interface. `Telemetry` is split so that the gate depends on flags alone and not on analytics
     or crashes.
4. **`FeatureGate` publishes the result**: `StateFlow<Set<Feature>>` and `isAvailable(feature)`, combined in a scope
   the composition root owns. View models receive the gate by constructor. A screen never reads Remote Config or the
   role by itself.
5. **The gate decides what is drawn, never what is allowed.** The backend's tier checks stay the authority (backend
   `ADR-0036`). A feature hidden by the gate whose route the user still reaches gets the server's 403/404, and the app
   treats it like any other refusal.
6. **Tests cover the whole decision**, not samples:
   - the pure function, over every feature × role state × flag value;
   - the gate, with fake sources that change over time: role arriving late, a flag flipping, the role lost at
     logout;
   - the catalog, through the exhaustive `when` and one test per rule.

## Consequences

- Logout publishes `Unknown` before the wipe finishes, so nothing role-gated outlives the session for a frame.
- A feature without a role restriction does not exist in this catalog: everything in the app after sign-in belongs to a
  role. Pre-auth screens are not features of this gate.
- A Remote Config outage leaves the app as compiled. Turning a feature off in an emergency needs Remote Config to
  answer; a forced closure of the whole app stays `/v1/app-availability`'s job (§8.15).
