# ADR-0056 — A doctor signs in through the patient's door

- **Status**: Accepted (2026-10-13), the author's decision by form. It amends ADR-0044: the doctor-linking screens
  (4a, 4a-e1, 4b, 4b-e1, 4c, and the «not linked» screen) are removed, and so is the «¿Eres médico?» link on the
  welcome screen.
- **Related**: ADR-0044 (the access flow); ADR-0053 (links by audience).

## Context

The welcome screen had a third door: «¿Eres médico? Vincula tu cuenta LumeMed». It led to a code taken from
LumeMed, a confirmation, and a doctor's app. The author removed it for security reasons:

- a separate entrance tells anyone who looks that this app has a way into LumeMed, and invites them to try it;
- two flows are two surfaces to keep hardened, and two sets of expectations about what each one can do.

## Decision

1. **There is one way in: RUT and password.** Doctors use it too. Nothing before sign-in asks whether someone is a
   doctor.
2. **The server says which app the account opens.** `PatientAccessGateway.signIn` answers `AccountKind.PATIENT` or
   `AccountKind.DOCTOR`, and the flow finishes with that role. A doctor is an account whose LumeMed side the server
   has already linked. Linking itself happens elsewhere, never here.
3. **This app never shows a doctor-linking code** and has no operation to look one up or confirm it.
4. In the script, the doctor's synthetic RUT signs in as a doctor. Every other RUT signs in as a patient.

## Consequences

- The `features/auth/doctor` screens, their route entries, the `DoctorLinkGateway`, the three linking failures and
  their strings are gone.
- The contract has no role in the sign-in answer yet. It has to be requested from the backend when patient and doctor
  sign-in are wired; until then the script answers it.
- An account that is a doctor in LumeMed but has not been linked signs in as a patient. Whether that should be told
  apart is the server's decision.
