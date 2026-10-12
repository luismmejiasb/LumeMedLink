# ADR-0049 — The clinic's address can be copied, marked sensitive

- **Status**: Accepted (2026-10-11) — the author's decision of this date, for f1's «Copiar dirección».
- **Related**: ADR-0013 (amended 2026-10-06), constitution §8.9, ADR-0048; `Scripts/check-input-surfaces.sh`; task `0030`.

## Context

§8.9 keeps personal data shown on screen off the shared clipboard, and the input gate refuses every clipboard API
because no caller existed. f1 offers «Copiar dirección». The value is the clinic's address, not the patient's. But
next to an appointment it says which specialist they see, and on Android 13 and later the system shows what was
copied in an overlay.

## Decision

1. **One value, one door.** `core/device` copies the appointment's place, and nothing else ever goes through it.
2. **Marked as what it is.** On Android the clip carries `ClipDescription.EXTRA_IS_SENSITIVE`. On 13 and later the
   system then hides it in its overlay and keyboard suggestions. Below 13 there is no such control, and that is said
   here rather than implied. On iOS the item is local only (`UIPasteboardOptionLocalOnly`, so Universal Clipboard does
   not carry it to another device) and expires after two minutes (`UIPasteboardOptionExpirationDate`).
3. **The gate keeps the rest closed.** The input gate lets the clipboard APIs appear only in
   `core/device/AddressClipboard.android.kt` and `.ios.kt`. Each must carry its marking (the sensitive extra, local-only
   and expiry), or the gate is red. Any other use, and any selectable text container, is still red. Baits prove each
   rule.

## Consequences

- A patient can paste the address into whatever they use to get there.
- Data the patient typed (RUT, phone) keeps ADR-0013's rule, and displayed personal data stays uncopyable.
