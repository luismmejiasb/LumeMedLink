# ADR-0055 — A screen waits as a skeleton of itself

- **Status**: Accepted (2026-10-13), the author's decision by form. Waits on the kit: LumeUIComposer has no
  placeholder mode yet, and building it is that repo's task. Until it lands, the rule is declared and not met.
- **Related**: constitution §5; ADR-0033 (the design system is LumeUIComposer); task `0034`.

## Context

Every screen that reads content shows a centred `LumeActivityIndicator` while it waits. There are 13 of them, and
they all go through `CareLoadView` in `core/screen`. When the content arrives, the spinner is replaced by a layout of
a different shape, so the page jumps under the reader's eyes.

The kit's `LumeSkeleton` draws generic shapes only: lines, rows, circles, and blocks sized by the caller. To make
each screen's wait look like that screen with it, the app would have to hand-build 13 skeletons with fixed sizes in
dp, and keep them in step with every change to the screens they copy.

## Decision

1. **A screen that waits for content shows a skeleton of itself, never a spinner.**
2. **The skeleton is the screen itself, drawn in the kit's placeholder mode.** It is the real composable with stand-in
   data, under a kit mode that draws every component as its own shimmer, as SwiftUI's `.redacted(reason:
   .placeholder)` does. The shape matches by construction, and no size is made up.
3. **A button that waits for an answer keeps its indicator.** «Ingresar» and «Continuar» are not waiting for a view;
   their indicator means "sending", and it stays where the person tapped.
4. Under the placeholder, nothing is interactive. A screen reader does not read the stand-in data: the screen
   announces the wait once.

## Consequences

- Task `0034` wires it into `CareLoadView` and gives each of the 13 screens its stand-in content. The stand-in is
  synthetic and generic (no name of a real person, §9), and it never leaves the screen.
- Until the kit delivers, the spinner stays. The rule is **[manual]**: a gate could later refuse
  `LumeActivityIndicator` in `features/`.
