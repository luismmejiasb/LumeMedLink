# ADR-0026 — Cancellation is not recognised by type; it is asked of the job

- **Status:** Accepted · 2026-08-25 (found while building F23's HTTP half)
- **Related:** §6 (structured concurrency), ADR-0016 and bitácora 0018 (the first time this bit),
  ADR-0023 (the security-event channel this was found under), ADR-0025 (the probe that shares it).

## Context

This repository has now been bitten twice by the same mistake, in shipped code, both times found by
accident.

**First (F12/ADR-0016).** `HttpRequestTimeoutException` **is** a `CancellationException`, so
re-throwing cancellation untouched let Ktor's message — which embeds the full URL and its query —
reach the caller. The fix was to catch the timeout *before* the cancellation branch.

**Second (here).** Building `HttpSecurityEventReporter` against the real stack, its cancellation
test failed. When the **caller's** scope dies mid-request, Ktor surfaces the engine's failure
**wrapped**: the `catch (cancellation: CancellationException)` branch never sees it, the broad
catch below maps it, and the caller receives an `AppError` for an event that is not a failure at
all. Two consequences, both real: a caller with retry logic re-sends a request the user just
cancelled, and the caller carries on inside a scope that is being destroyed (§6).

The lesson is not "add another type to the list". Both incidents are the same shape: **a type-based
check answers the wrong question.** The question that discriminates is not *what is this exception*
but *is my job still alive*.

## Decision

**1. Every broad catch calls `currentCoroutineContext().ensureActive()`.** It re-throws the
caller's own cancellation when the scope is gone and does nothing when the failure is real — which
is exactly when swallowing or mapping is correct. It is right for wrappers that do not exist yet,
which a type list can never be.

**2. `Scripts/check-cancellation-guard.sh` enforces it**, at file granularity and honest about that
limit: it does not prove the guard is in the *right* catch, which is why each guard also carries its
own bait-tested test. What it does catch is the case that actually happens — somebody adds a broad
catch and never thinks about cancellation at all.

**3. `LumeHttpStack` is the one registered exception, with its reason written down** — the same
discipline the backend's `UNENFORCEABLE_PATIENT_ROUTES` uses. Its broad catch runs inside a Ktor
plugin hook, which is **not** the caller's job, so `ensureActive()` there has nothing cancelled to
observe. That was measured, not assumed: the guard was added there first and did not fire.

**4. What the stack does instead is characterised by a test, not described by a comment.**
`LumeHttpStackTest.aCancelledCallerSeesRetryableNotCancellation` pins the behaviour, and its name
records that the first draft of the assertion said `Retryable` and the measured value was
`Unexpected`. A comment would have shipped the wrong value; the test could not.

## Consequences

- Three call sites carry the guard today — the security-event reporter, the launch session probe,
  and any future one the gate will demand it of.
- The sharp edge remains at the stack boundary: a cancelled caller of `lumeHttpClient` receives an
  `AppError`. That is now a documented, tested decision rather than a surprise, and the guard that
  neutralises it is required of every caller by CI.
- **If that test ever starts failing because the caller stops carrying on, that is an improvement.**
  The test says so in its own KDoc, so nobody restores the old behaviour to make CI green.

## A note on how this was nearly recorded wrong

The first two runs of the experiment that decided point 3 were **green and red for the wrong
reason**: the new test had been appended into the wrong class, so a `--tests` filter matched nothing,
and Gradle's "no tests found" failure read as a bait working. On that evidence the conclusion was
"the stack guard is load-bearing" — the exact opposite of the truth. It was caught only when the
full suite ran and the test finally executed.

Two habits come out of it, and they are cheap: **check the test count, not just the build status**,
and **put a new test in the class you think you put it in.** This is the eighth green-for-the-wrong-
reason recorded in this repo and the first one produced by the verification harness rather than by
the code under test.
