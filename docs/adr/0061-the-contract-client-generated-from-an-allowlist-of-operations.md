# ADR-0061 — The contract's client: generated from an allowlist of operations, committed, checked for drift

- **Status**: Accepted (2026-10-14). It closes the generator question ADR-0004 left «decided at wiring». The author
  approved the plan that names it; the tool's choice is argued below.
- **Mirrors**: LumeMed's ADR-0027 (generated, committed, the dev route merged in, drift checked). **Differs** in the
  tool, for the reasons in Context.
- **Related**: ADR-0001 and ADR-0019 (the data boundary and its gate), ADR-0004 (the hardened stack), ADR-0058,
  ADR-0059; trap T1; the platform's contract `packages/contracts/openapi.json`.

## Context

The platform's contract (0.127.0 on 2026-10-14) has 206 operations, most of them clinical: diagnoses, exams,
medication, the record. This app may consume a handful, under `/v1/patient/*`, `/v1/directory/*`, `/v1/reference/*`
and `/v1/me`; never `/v1/orgs/*` (LumeMed's answer of 2026-10-14). The 18 operations of the four areas the author
named (appointments, directory and booking, profile, access) reach 19 schemas: no `oneOf`, `allOf` or discriminator,
and five enums.

A general generator (openapi-generator, LumeMed's `swift-openapi-generator`) emits the whole contract. Here that would
put the clinical models inside the binary: a violation of ADR-0001 that `check-data-boundary.sh` would rightly fail,
and a door for the next feature to use them. Its enums are also closed: a status the app does not know fails to
decode. The contract's statuses are an open vocabulary, and LumeMed reads an unknown one as opaque, never as an error.

## Decision

1. **The contract is vendored byte-identical** under `contract/openapi.json`. Its SHA-256 and the platform commit it
   came from are recorded in `contract/PIN`.
2. **An allowlist names every operation the app may call**: `contract/operations.txt`, one `operationId` per line,
   each with the area that uses it. An operation outside `/v1/patient/`, `/v1/directory/`, `/v1/reference/` and
   `/v1/me` is refused by the generator itself, so that the boundary is a property of the build, not of good intentions.
3. **Our own generator**, `Scripts/generate-contract.py`, emits only what the allowlist reaches:
   - `@Serializable` data classes, `internal`, for every reachable schema;
   - **open enums**: a value class over the wire `String`, with known cases as constants, so an unknown value
     decodes and stays opaque;
   - an operations table (method, path template, request and response types), so that no path is typed by hand;
   - the output goes to `core/networking/contract/`, committed.
   No third-party tool enters the build or the repo.
4. **The calls are written over the hardened stack** (ADR-0004), one small class per area, using the operations
   table. The generated code never builds its own HTTP client.
5. **The dev route travels in a merge**, as in LumeMed: `contract/dev-auth.json` describes the backend's
   `POST /dev/token`, which the contract leaves out on purpose. It is reachable only from debug builds, and the gate
   refuses it in `src/*Main` of a release source set.
6. **`Scripts/check-contract.sh`** fails CI when:
   - the vendored file does not match `PIN`;
   - the generated code differs from a fresh run (drift);
   - an operation is used that is not in the allowlist;
   - the allowlist names an operation outside the four prefixes.
   Each condition has its bait in `rehearse-gates.sh` (ADR-0029).
7. **Updating the contract is a reviewable diff**: refresh the vendored file and `PIN`, regenerate, and read what moved.
   An incompatible change breaks compilation, which is the ecosystem's promise.

## Consequences

- The generator covers what the 19 schemas need. A construct it does not know (`oneOf`, `allOf`, a discriminator)
  stops the run with its name, instead of generating something wrong; supporting it is a change to the script, with a
  test.
- Adding a feature that needs a new operation is one line in the allowlist and a regenerate, reviewed like code.
- The client knows nothing clinical, because nothing clinical is generated. The data-boundary gate keeps meaning
  what it says.
