# ADR-0047 — The mail app opens with an address and nothing else

- **Status**: Accepted (2026-10-11) — the author's decision of this date, over the deviation ADR-0046 recorded.
- **Related**: ADR-0007 (amended here, narrowly), ADR-0001, ADR-0029, ADR-0046 (deviation 4); `Scripts/check-forbidden-patterns.sh`
  P2; task `0030`.

## Context

Mis médicos (p6) draws «Correo» next to «Llamar» for a doctor who shares an address with their patients. On Android,
handing an address to the mail app is `Intent.ACTION_SENDTO` with a `mailto:` URI, and that is the family of intents
ADR-0007 closes with no exceptions: the gate P2 refuses every `ACTION_SEND*`, because a send intent can carry an
attachment, and an attachment is how a document with legal value would leave this app (backend trap T1).

What p6 needs is not a send. It is the address, given to the mail app, with nothing attached and nothing written.

## Decision

1. **One door, one shape.** `core/device` opens the mail app with `mailto:` and the address. No attachment, no
   subject, no body, no carbon copies, no stream, no clip data. On Android it is `Intent.ACTION_SENDTO` with
   `Uri.fromParts("mailto", address, null)`; on iOS it is `openURL` with `mailto:` and the address.
2. **The address is checked before it leaves.** It must be one address with a single `@` and none of the characters
   that open a `mailto:` query or fragment, or a second recipient (`?`, `&`, `#`, `%`, `,`, `;`, `/`, `\`, `:`, a
   space, a control character). An address that fails the check opens nothing. A value written by somebody else (the
   doctor, through the backend) never gets the chance to add a subject, a body or an attachment.
3. **The gate keeps the rest of ADR-0007 total.** P2 lets that one line pass, and only in
   `core/device/DeviceContact.android.kt`. Any other send intent anywhere, a second `ACTION_SENDTO` in that file, or an
   extra, a type, a clip or a stream there, is still red. `Scripts/rehearse-gates.sh` carries a bait for each.
4. **ADR-0007 stands otherwise.** This app still never shows, stores, relays or links to a clinical document. The
   mail app gets an address. What the person then writes and attaches is theirs, in their own mail app, and never
   passes through this one.

## Consequences

- ADR-0046 deviation 4 closes: «Correo» opens the mail app.
- A future need to prefill anything (a subject naming the clinic, say) is not covered here. It needs a new ADR,
  because prefilled text is content this app would author.
- The same door serves the doctor's side (m2): a patient's address, by the author's call of 2026-10-11. What the doctor
  writes or attaches in their own mail app never passes through this one.
- The exception is narrow enough to be read in one line of the gate. Widening it means editing that line, which is the
  point.
