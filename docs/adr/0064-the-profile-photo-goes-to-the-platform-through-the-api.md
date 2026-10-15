# ADR-0064 — The profile photo goes to the platform through the API

- **Status**: Accepted (2026-10-14), the author's decisions by form. Its upload waits on the contract (backend request
  `0015`); until then ADR-0050 holds as written.
- **Related**: ADR-0050 (picked in memory, never stored), ADR-0022 (at-rest bytes), ADR-0004 and §7 (no egress outside
  the stack), ADR-0001 (the photo is on the closed list), §8.13 (logout wipe), the backend's `ADR-0035` (F6), task
  `0036`.

## Context

ADR-0050 built the photo flow in memory because the contract had nowhere to send it. The backend's `ADR-0035` lists
the photo as new construction (F6): there is no field, no storage and no retention, and the nearest thing,
`medical-files`, would turn a face into a clinical document.

The author decided two things on 2026-10-14:

- the photo is seen by the patient and by their doctors;
- it reaches the platform through the API, not through a signed upload URL.

## Decision

1. **One photo per account.** It is not stored per clinic: the app shows one profile (task `0036`).
2. **Up through the stack.** The bytes go in a contract operation over `lumeHttpClient`, like every other call. A
   signed URL to a storage host is refused, because the stack pins the backend's origin and an exception for a second
   host is a second egress (§7).
3. **The app sends little.** It sends the cropped, scaled bitmap of ADR-0050, encoded as JPEG. The app writes no
   metadata, and the server re-encodes the image anyway (request `0015` §3).
4. **Down through the stack.** The view receives a bitmap, never a URL (§7, as for any remote image). It is held in
   memory with the session. Keeping it between sessions needs ADR-0022's encrypted store and the logout wipe, and is
   not decided here.
5. **Who reads it is the platform's to enforce.** The patient and the professionals of their clinics, never across a
   cut link or a restriction of processing (request `0015` §5). The app shows its own photo; the doctor's side shows a
   patient's only once that route exists in the contract.

## Consequences

- ADR-0050 §2 («never uploaded») is superseded when the contract lands. Its in-memory picking and its doors stand.
- k3's «No pudimos subir tu foto · Reintentar» gets a real failure from the upload.
- A face next to an appointment with a specialist reveals more than either alone (§1.0). The doctor's view is
  therefore limited to the patient's own clinics, by the platform, never by the app.

## Progress (2026-10-15)

- **Built behind a protocol, not connected.** `core/care/ProfilePhoto.kt`: `ProfilePhotoGateway` (read, upload,
  remove), `ProfilePhotoSync` (k3 keeps the previous photo on a failed upload and retries the same one; a read that
  is refused, missing or failed is the initials, in silence — ADR-0067), and `InMemoryProfilePhoto`, the platform's
  side held in memory inside the session's store until the contract lands. `core/device/PhotoCodec` turns the crop
  into JPEG and the platform's bytes back into a bitmap. Removing is confirmed. A sign-out clears it (§8.13).
- **Still pending:** the contract (backend task `0150`, PENDING; contract `0.132.0` has no photo operations). The
  remote gateway, with 403 and 404 as «no photo», is written when it lands, and only then is ADR-0050 §2 closed as
  superseded.
- **The doctor's photo** is its own request (`0016`, the author's call of 2026-10-15). Until it has a contract, the
  doctor's own photo in m3 stays in memory as ADR-0050 built it.

