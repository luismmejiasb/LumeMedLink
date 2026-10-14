# ADR-0057 — Settings carries the theme, the language and the legal pages

- **Status**: Accepted (2026-10-13), the author's decisions after testing on a device; two of them chosen by form.
- **Related**: ADR-0052 §2 (the telemetry consent), ADR-0045 (the patient's tabs), §8.13 (logout = wipe), task `0035`.

## Context

Testing on an iPhone, the author asked Configuración for what an app's settings usually carry and this one lacked: the
theme (light, dark, the phone's), the language (Spanish, English, the phone's), «Privacidad», «Términos y
condiciones» and «Licencias», the copyright and the version at the bottom, and a confirmation before «Cerrar sesión».
The consent switch for errors and statistics moves from the root into «Privacidad».

There is no legal text yet (the lawyer's commission is open), and no list of licences.

## Decision

1. **Theme and language default to the phone's**, and are chosen on pushed pages with a single choice each.
   - The theme feeds `LumeTheme(dark = …)` at the root.
   - The language is applied per platform under `core/appearance`. Android sets the default locale and the
     configuration the composition reads. iOS sets `AppleLanguages` in the app's own defaults, which is iOS's per-app
     language. «Sistema» removes the override.
2. **Both live in the secure store**, beside the telemetry consent. They are not secret, but kept there the logout's
   wipe takes them, and a shared phone's next person starts on the phone's own (§8.17).
3. **«Licencias» is generated, never written by hand** (author, by form). `Scripts/generate-licenses.py` reads what
   ships: the Android release classpath and the iOS klibs from the lockfiles, and the Swift packages from
   `Package.resolved`. It names each group through `config/licenses.txt`. A shipped group without a line fails, and
   `Scripts/check-licenses.sh` fails CI when the generated file is stale. No dependency was added. Our own kit is not
   a third party and is not listed.
4. **Privacy and terms show a draft until the lawyer's text** (author, by form). The pages say «Borrador» on screen, and
   the text is PROVISIONAL here. It claims only what the code does: the data boundary, encryption in transit, the
   wipe on sign-out, and the optional telemetry.
5. **Sign-out asks first**, with a `LumeAlert` whose confirming action is destructive.
6. **The copyright states the year of first publication**, and the version is read from the installed build (the
   bundle, the package), never from a constant.

## Consequences

- A new dependency now needs two lines: its group in the allowlist and its licence in `config/licenses.txt`. That is
  the point.
- The licence names were written down by hand from each project's own declaration. They are checked by the lawyer
  together with the legal texts, before the stores.
- On Android, the language override lasts for the process; at launch the stored choice is applied again before
  anything is drawn. On iOS, `AppleLanguages` persists by itself, and the stored choice wins over it on every launch.
- The doctor's profile does not have these pages yet.
