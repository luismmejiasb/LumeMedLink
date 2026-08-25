# ADR-0024 — The structure-export channels: what Compose opts into on the app's behalf

- **Status:** Accepted · 2026-08-25 (fortification slice F3, reopened)
- **Related:** ADR-0013 (clipboard and keyboard — this is the same slice, reopened by a finding);
  ADR-0010 (FLAG_SECURE app-wide, which turns out to cover one of these channels and not another);
  §8.9/§8.10 of this constitution; threat model T4 (passive platform exfiltration).
- **Supersedes nothing.** It corrects one sentence of ADR-0013 that claimed autofill exclusion was
  unreachable. It was unreachable where ADR-0013 looked; it is reachable one level up.

## Context

F3 closed on 2026-08-21 and was reopened the same day by a finding from F6: Compose sets
`getImportantForAutofill()` to YES, so the autofill framework receives a virtual structure for
Compose screens without the app asking, and FLAG_SECURE does not touch it. That finding was
recorded as **reported, not verified by me** — this ADR is what verification turned it into.

Verification changed three things about it.

**It is narrower than "every screen".** `AndroidAutofillManager.populateViewStructure` exports only
nodes whose semantics satisfy `isRelatedToAutofill()` — `ContentType`, `ContentDataType`,
`OnAutofillText` or `OnFillData`. A read-only `Text()` showing a patient's name is *not* exported.

**And within that, it is total.** Every Compose text field sets `contentDataType =
ContentDataType.Text` unconditionally — `TextFieldDecoratorModifier.kt:560` and
`CoreTextFieldSemanticsModifier.kt:137`, with no condition, no parameter and no opt-out. So the set
"fields this app takes personal data in" and the set "nodes handed to the user's autofill service"
were the same set. The exposure is real and it is exactly the surface S1.4 will draw: a doctor
typing a patient's RUT or phone into a field.

**And the obvious remedy silently does nothing.** `AndroidComposeView` overrides
`getImportantForAutofill()` to `return IMPORTANT_FOR_AUTOFILL_YES` with **no backing field read**.
Assigning `composeView.importantForAutofill = …` therefore writes a value the getter never
consults, and the setter reports no error. This is the seventh recorded case in this repo of a
change that would have looked applied while doing nothing — and the first one caught *before*
shipping rather than after.

FLAG_SECURE does not help and cannot: banking apps set FLAG_SECURE and still autofill. The two
mechanisms answer different questions — FLAG_SECURE governs *pixels*, this governs *structure*.

## Decision

**1. Exclude the whole window from the autofill structure, from an ancestor.**
`View.isImportantForAutofill()` walks up the parents *before* consulting the view's own value and
hard-returns `false` at the first ancestor marked `IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS`
(read in the platform source at android-36.1, not recalled). The Android shell therefore calls
`window.decorView.denyAutofillExport()`, and the decision plus its reasoning live in
`core/input/StructureExport.kt` rather than at the call site, so the trap is documented where the
next reader will be standing.

**2. Raise the app's own content-capture flag, and do not claim it as a fix.** Compose force-sets
`IMPORTANT_FOR_CONTENT_CAPTURE_YES` when it acquires a session, but that channel is **already shut
here by FLAG_SECURE**: `ContentCaptureManager.updateWindowAttributes` raises
`FLAG_DISABLED_BY_FLAG_SECURE` for a secure window. `denyContentCapture()` raises the *independent*
`FLAG_DISABLED_BY_APP` so the channel survives a future screen that loses FLAG_SECURE. It closes
nothing today, and the code says so.

**3. Record that the assist channel is empty by construction, and pull no lever.** The assist walk
uses `onProvideVirtualStructure`, which Compose does not implement at all — only the autofill
variant. Compose content is not made of Views, so assist gets a node for the Compose view and
nothing beneath it. Writing a "fix" here would buy nothing and would read, later, as a control that
exists.

**4. Gate both halves, as an allowlist.** `Scripts/check-input-surfaces.sh` now asserts the two
calls are present *and on the decor view*, and that every assignment to `importantForAutofill` is
`NO_EXCLUDE_DESCENDANTS` and every `setContentCaptureEnabled` call passes `false`.

The allowlist shape is not stylistic. The first draft of this gate was a denylist naming the bad
value, and **its own bait walked through it**: the pattern accepted the qualifier `View.` and the
bait wrote `android.view.View.IMPORTANT_FOR_AUTOFILL_YES`. That is the same failure as the
`androidx.` prefix hole in F20 — naming bad values catches only the spellings someone thought of.
The inverted rule was then re-baited in seven spellings, including a raw numeric literal.

**5. Keep the per-purpose distinction of ADR-0013 alive, because this decision endangers it.** The
exclusion is window-wide, so it also excludes a password field — and a password manager is a
security *gain*. No credential screen exists yet (F11 is blocked on the backend). When one arrives
it must ask for autofill back explicitly, for that window, and that is a decision with its own
review — not something to be rediscovered as a bug report about a login screen where the password
manager stopped working.

## Consequences

- The automatic autofill path — the one that fires merely because a field took focus — no longer
  receives this app's fields. That is the entire silent exposure.
- **Residual, declared.** `AssistStructure.resolveViewAutofillFlags` re-admits excluded views under
  exactly three conditions: a **manual** request (the user long-presses and chooses Autofill),
  autofill **compatibility mode**, and **PCC detection**. A user who deliberately asks for autofill
  still gets it. Compatibility mode is the uncomfortable one: it is enabled by the autofill
  *service's* own metadata listing our package, so it is not ours to refuse. Unverified on device —
  proving it needs an autofill service written for the purpose, which is not this slice.
- **What is proven on the device, and what is not.** `StructureExportTest` proves, with a control,
  that an excluded ancestor beats a hardcoded YES child and reaches grandchildren, on a real
  Android runtime. It does **not** drive a live Compose hierarchy: the view under test is a
  stand-in reproducing the one behaviour of `AndroidComposeView` that matters here. That Compose's
  view really carries that override is proven by its source at the version the lockfile pins, and
  the test says so in its own KDoc rather than in this ADR alone.
- The content-capture assertion was written expecting to have **no control**, and measurement said
  otherwise: on the Pixel 9 emulator at API 37 the channel is enabled before `denyContentCapture()`
  and disabled after, because that image ships Android System Intelligence as the content-capture
  service. So the before/after is real there. The limit survives only in its narrow form — on a
  device with no such service the value is already false and the control proves nothing — and the
  test reports which case each run hit instead of letting the reader assume the good one.
- iOS is untouched by all of this. It has its own structure-export surfaces and its own answer, and
  neither can be written before the host exists.
