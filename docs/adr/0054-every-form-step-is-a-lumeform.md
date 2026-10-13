# ADR-0054 — Every form step is a LumeForm

- **Status**: Accepted (2026-10-13), the author's decision by form. Waits on the kit: LumeUIComposer has no
  `LumeForm` yet, and building it is that repo's task. Until it lands, the rule is declared and not met.
- **Related**: constitution §5; ADR-0033 (the design system is LumeUIComposer); ADR-0044 (the sign-up design);
  task `0033` (the migration).

## Context

The author found the sign-up's password step (2b) hard to use:

- The password checklist sits far from its field and looks like it belongs to "Repetir contraseña". Every kit field
  reserves a caption line, and the checklist comes after that line.
- The consents are part of "Continuar": they are what enables it. They scroll with the fields, far apart. And the kit's
  action bar rises with the keyboard (`imePadding`), so anything placed beside the button would rise too.
- The keyboard should carry the person through: "Siguiente" from one field to the next, and on the last one an action
  that closes the keyboard and leaves the consents and the button in view.

These problems are not specific to 2b. Every step built as `LumeContainer` + `LumeStack` has them, and fixing them one
screen at a time would not fix them for the next screen.

## Decision

1. **Every form step in this app is a `LumeForm`**, the kit's component (to be built in LumeUIComposer). That covers
   the sign-up steps (2a, 2b, 2c), recovery, sign-in, and every later form.
2. What a `LumeForm` guarantees, as this app needs it:
   - **The fields** scroll, and only they move with the keyboard.
   - **The footer** holds the consents and the primary action, anchored to the bottom edge. It stays under the
     keyboard instead of rising with it.
   - **The keyboard chain**: each field's action is "Siguiente" to the next field. The last field's action closes the
     keyboard and brings the footer into view.
   - **A field's help is attached to it**, such as the password checklist, with no reserved empty line between them.
3. The API and its parity with the twin are the kit's decisions. This ADR fixes only what the app relies on.

## Consequences

- Task `0033` migrates the steps once the kit delivers. Until then the sign-up keeps `LumeContainer`. Only one fix
  is made now: the consents are grouped tighter.
- **Platform limit:** the keyboard key cannot read "Continuar". Compose exposes only the action (`ImeAction.Done`), and
  the keyboard draws its own symbol for it.
- On sign-in, the link goes under the pinned action, in the kit's `belowAction` slot. The single-datum screens
  (codes, the recovery RUT) keep their horizontally centred stack inside a top-aligned `LumeForm`. Their primary
  action is pinned now, including 3a's, which used to sit under its field (2026-10-13).
- The kit stopped reserving a field's message line, and a form's fields sit 12 dp apart. An error now pushes what is
  under it: the author accepted that the form moves (2026-10-13).
- No gate checks this rule yet: it stays **[manual]** until `LumeForm` exists. A gate would then look for
  `LumeContainer` around a field in `features/`.
