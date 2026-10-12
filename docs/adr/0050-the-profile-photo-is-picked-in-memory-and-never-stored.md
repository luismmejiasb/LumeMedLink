# ADR-0050 — The profile photo is picked in memory and never stored

- **Status**: Accepted (2026-10-11) — the author's decision of this date, for k1–k4: build the photo flow in full as a
  mock, in memory.
- **Related**: ADR-0007, ADR-0046 (deviation 6), ADR-0022 (at-rest bytes), constitution §8.5 and §8.13; task `0030`.

## Context

k1–k4 let the patient (p7) and the doctor (m3) change their profile photo: take one or pick one, crop it to the circle,
and see it replace their initials. The contract has no photo field (ADR-0046, deviation 6), so there is nowhere to send
it yet. A photo of a face is personal data, and any bytes at rest must go through `core/session`'s encrypted store
(ADR-0022).

## Decision

1. **The two doors ask as little as the platforms allow.**
   - Android uses the system photo picker, which needs no permission and shows the app only the photo chosen, and the
     camera's preview, which returns a bitmap without a file. So no storage permission and no `CAMERA` permission are
     declared, and no file is shared. Registering those results belongs to the activity, so the shell (`MainActivity`)
     owns the two launchers and hands them to `core/device` as `AndroidPhotoHost`.
   - iOS uses PHPicker, which needs no photo-library permission, and the camera, which asks the first time it is used
     (`NSCameraUsageDescription`). A refused camera is k4, with «Abrir Ajustes».
2. **In memory only.** The photo is decoded into a bitmap, scaled down, cropped, and held by the session's owner. It is
   never written to disk, never cached, never uploaded, and a sign-out takes it with the session's store (§8.13).
3. **Nothing new on the dependency list.** The Android launchers use `androidx.activity`, which the shell already
   carries.

## Consequences

- Until the contract has a photo field, the photo lasts as long as the session. When it does, the upload goes through
  the networking stack, and k3's «No pudimos subir tu foto · Reintentar» gets its failure.
- A photo kept on the phone between sessions would need ADR-0022's encrypted store and the logout wipe. This decision
  does not cover it.
